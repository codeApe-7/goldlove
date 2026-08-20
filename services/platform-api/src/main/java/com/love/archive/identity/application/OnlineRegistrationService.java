package com.love.archive.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.web.ApiException;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.domain.MembershipTier;
import com.love.archive.identity.domain.PasswordPolicy;
import com.love.archive.identity.domain.PhoneNormalizer;
import com.love.archive.identity.domain.RegistrationChannel;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.identity.security.PhoneProtector;
import com.love.archive.identity.web.GuestSessionView;
import com.love.archive.payment.application.PaidRegistrationOrder;
import com.love.archive.payment.application.RegistrationTokenService;
import java.time.OffsetDateTime;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 线上注册：把「激活 + 建档账号」合并成一步。校验注册令牌与已支付订单后，
 * 在单个事务内建账号（ACTIVE + VIP）、标记订单已注册并累计会员额度。
 * 不绑定支付者外部身份（易支付无稳定 openid）；手动路径（登记 → 邀请码 → 激活）保持原样。
 */
@Service
@RequiredArgsConstructor
public class OnlineRegistrationService {

    private final RegistrationTokenService registrationTokenService;
    private final MembershipService membershipService;
    private final RegistrationPhoneToken registrationPhoneToken;
    private final UserAccountMapper userAccountMapper;
    private final AuditTrail auditTrail;
    private final PhoneNormalizer phoneNormalizer;
    private final PhoneProtector phoneProtector;
    private final PasswordHasher passwordHasher;

    @Transactional
    public GuestSessionView register(OnlineRegistrationCommand command) {
        PasswordPolicy.validate(command.password());
        String phone = normalizePhone(command.phone());
        PaidRegistrationOrder order = registrationTokenService.lockPaidOrder(command.registrationToken());
        requireSamePhoneAsOrder(order, phone);

        String phoneHmac = phoneProtector.searchHash(phone);
        if (userAccountMapper.selectCount(Wrappers.<UserAccountEntity>lambdaQuery()
                .eq(UserAccountEntity::getPhoneHmac, phoneHmac)) > 0) {
            throw accountAlreadyExists();
        }

        OffsetDateTime now = OffsetDateTime.now();
        UserAccountEntity account = new UserAccountEntity();
        account.setPhoneCiphertext(phoneProtector.encrypt(phone));
        account.setPhoneHmac(phoneHmac);
        account.setPasswordHash(hash(command.password()));
        account.setStatus(AccountStatus.ACTIVE);
        account.setRegistrationChannel(RegistrationChannel.ONLINE);
        account.setMembershipTier(MembershipTier.VIP);
        account.setMembershipCreditMinor(0L);
        account.setActivatedAt(now);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        try {
            userAccountMapper.insert(account);
        } catch (DataIntegrityViolationException exception) {
            if (containsConstraint(exception, "uq_user_account_phone_hmac")) {
                throw accountAlreadyExists();
            }
            throw exception;
        }

        registrationTokenService.completeRegistration(
                order.tokenId(), order.paymentRecordId(), account.getId());
        membershipService.creditPayment(account.getId(), order.paymentRecordId(), order.creditMinor());

        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.GUEST,
                account.getId(),
                "GUEST_ACCOUNT_REGISTERED_ONLINE",
                "USER_ACCOUNT",
                account.getId(),
                command.requestId(),
                "{\"outTradeNo\":\"" + order.outTradeNo() + "\"}",
                now));
        return new GuestSessionView(account.getId(), AccountStatus.ACTIVE, null, 0L);
    }

    private String normalizePhone(String rawPhone) {
        try {
            return phoneNormalizer.normalize(rawPhone);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "手机号格式不正确");
        }
    }

    /**
     * 手机号在下单时已经预检并钉在订单上，注册时必须一致，否则一笔付款会被挪给别的号码。
     * phoneToken 为 null 表示订单建立于 V11 之前，此时无从比对，沿用原有的唯一性校验。
     */
    private void requireSamePhoneAsOrder(PaidRegistrationOrder order, String normalizedPhone) {
        if (order.phoneToken() != null
                && !order.phoneToken().equals(registrationPhoneToken.of(normalizedPhone))) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "REGISTRATION_PHONE_MISMATCH", "手机号与下单时不一致");
        }
    }

    private String hash(String password) {
        char[] characters = password.toCharArray();
        try {
            return passwordHasher.hash(characters);
        } finally {
            Arrays.fill(characters, '\0');
        }
    }

    private static ApiException accountAlreadyExists() {
        return new ApiException(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", "该手机号已存在账号");
    }

    private static boolean containsConstraint(Throwable exception, String constraintName) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(constraintName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
