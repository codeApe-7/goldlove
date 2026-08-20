package com.love.archive.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.web.ApiException;
import com.love.archive.consent.application.ConsentEvidenceCommand;
import com.love.archive.consent.application.ConsentService;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.domain.MembershipTier;
import com.love.archive.identity.domain.PasswordPolicy;
import com.love.archive.identity.domain.PhoneNormalizer;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.identity.web.GuestSessionView;
import java.time.OffsetDateTime;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 免费自助注册：手机号 + 密码即可建号，账号直接 ACTIVE + FREE。
 * 同意授权书是注册表单上的勾选框，在同一个事务里写成同意记录。
 */
@Service
@RequiredArgsConstructor
public class SelfRegistrationService {

    private final UserAccountMapper userAccountMapper;
    private final ConsentService consentService;
    private final AuditTrail auditTrail;
    private final PhoneNormalizer phoneNormalizer;
    private final PasswordHasher passwordHasher;

    @Transactional
    public GuestSessionView register(SelfRegistrationCommand command) {
        String phone = normalizePhone(command.phone());
        PasswordPolicy.validate(command.password());
        if (!command.password().equals(command.confirmPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "PASSWORD_CONFIRMATION_MISMATCH", "两次输入的密码不一致");
        }
        if (!command.acceptedAuthorization()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "CONSENT_ACCEPTANCE_REQUIRED", "必须阅读并同意授权书");
        }
        if (userAccountMapper.selectCount(Wrappers.<UserAccountEntity>lambdaQuery()
                .eq(UserAccountEntity::getPhone, phone)) > 0) {
            throw accountAlreadyExists();
        }

        OffsetDateTime now = OffsetDateTime.now();
        UserAccountEntity account = new UserAccountEntity();
        account.setPhone(phone);
        account.setPasswordHash(hash(command.password()));
        account.setStatus(AccountStatus.ACTIVE);
        account.setMembershipTier(MembershipTier.FREE);
        account.setMembershipCreditMinor(0L);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        try {
            userAccountMapper.insert(account);
        } catch (DataIntegrityViolationException exception) {
            // 唯一索引是权威判定：预检和插入之间仍可能有人抢注。
            if (containsConstraint(exception, "uq_user_account_phone")) {
                throw accountAlreadyExists();
            }
            throw exception;
        }

        consentService.recordRegistrationConsent(account.getId(), new ConsentEvidenceCommand(
                command.authorizationDocumentVersion(),
                true,
                null,
                command.clientIp(),
                command.userAgent(),
                null));

        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.GUEST,
                account.getId(),
                "GUEST_ACCOUNT_REGISTERED",
                "USER_ACCOUNT",
                account.getId(),
                command.requestId(),
                "{}",
                now));
        return new GuestSessionView(account.getId(), AccountStatus.ACTIVE, MembershipTier.FREE, null, 0L);
    }

    private String normalizePhone(String rawPhone) {
        try {
            return phoneNormalizer.normalize(rawPhone);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "手机号格式不正确");
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
