package com.love.archive.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.web.ApiException;
import com.love.archive.identity.config.IdentitySecurityProperties;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.domain.PhoneNormalizer;
import com.love.archive.identity.persistence.ActivationCredentialEntity;
import com.love.archive.identity.persistence.ActivationCredentialMapper;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.InitialCredentialGenerator;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.identity.security.PhoneProtector;
import com.love.archive.identity.web.ProvisionedGuestView;
import com.love.archive.payment.application.PaidPayment;
import com.love.archive.payment.application.PaymentRecorder;
import com.love.archive.payment.application.PaymentReferenceConflictException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestProvisioningService {

    private final UserAccountMapper userAccountMapper;
    private final PaymentRecorder paymentRecorder;
    private final ActivationCredentialMapper activationCredentialMapper;
    private final AuditTrail auditTrail;
    private final PhoneNormalizer phoneNormalizer;
    private final PhoneProtector phoneProtector;
    private final InitialCredentialGenerator credentialGenerator;
    private final PasswordHasher passwordHasher;
    private final IdentitySecurityProperties securityProperties;

    @Transactional
    public ProvisionedGuestView provision(
            long adminId,
            String rawPhone,
            String paymentReference,
            long amountMinor,
            OffsetDateTime paidAt,
            String note,
            String requestId) {
        String phone;
        try {
            phone = phoneNormalizer.normalize(rawPhone);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "手机号格式不正确");
        }
        String phoneHmac = phoneProtector.searchHash(phone);
        if (userAccountMapper.selectCount(
                Wrappers.<UserAccountEntity>lambdaQuery().eq(UserAccountEntity::getPhoneHmac, phoneHmac)) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", "该手机号已存在账号");
        }

        OffsetDateTime now = OffsetDateTime.now();
        UserAccountEntity account = new UserAccountEntity();
        account.setPhoneCiphertext(phoneProtector.encrypt(phone));
        account.setPhoneHmac(phoneHmac);
        account.setStatus(AccountStatus.PAID_PENDING_ACTIVATION);
        account.setCreatedByAdminId(adminId);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        try {
            userAccountMapper.insert(account);
        } catch (DataIntegrityViolationException exception) {
            if (containsConstraint(exception, "uq_user_account_phone_hmac")) {
                throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", "该手机号已存在账号");
            }
            throw exception;
        }

        try {
            paymentRecorder.recordPaid(new PaidPayment(
                    account.getId(), paymentReference, amountMinor, paidAt, adminId, note, now));
        } catch (PaymentReferenceConflictException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_REFERENCE_EXISTS", "支付流水号已存在");
        }

        String initialCredential = credentialGenerator.generate();
        char[] credentialChars = initialCredential.toCharArray();
        String credentialHash;
        try {
            credentialHash = passwordHasher.hash(credentialChars);
        } finally {
            Arrays.fill(credentialChars, '\0');
        }
        OffsetDateTime expiresAt = now.plus(securityProperties.getActivationTtl());

        ActivationCredentialEntity credential = new ActivationCredentialEntity();
        credential.setUserAccountId(account.getId());
        credential.setCredentialHash(credentialHash);
        credential.setExpiresAt(expiresAt);
        credential.setCreatedByAdminId(adminId);
        credential.setCreatedAt(now);
        activationCredentialMapper.insert(credential);

        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.ADMIN,
                adminId,
                "GUEST_ACCOUNT_PROVISIONED",
                "USER_ACCOUNT",
                account.getId(),
                requestId,
                "{\"paymentReference\":\"" + paymentReference + "\"}",
                now));

        return new ProvisionedGuestView(
                account.getId(), AccountStatus.PAID_PENDING_ACTIVATION, initialCredential, expiresAt);
    }

    @Transactional
    public ProvisionedGuestView reissueActivationCredential(long adminId, String rawPhone, String requestId) {
        String phone;
        try {
            phone = phoneNormalizer.normalize(rawPhone);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "手机号格式不正确");
        }
        UserAccountEntity account = userAccountMapper.selectOne(
                Wrappers.<UserAccountEntity>lambdaQuery()
                        .eq(UserAccountEntity::getPhoneHmac, phoneProtector.searchHash(phone))
                        .last("FOR UPDATE"));
        if (account == null || account.getStatus() != AccountStatus.PAID_PENDING_ACTIVATION) {
            throw reissueUnavailable();
        }

        OffsetDateTime now = OffsetDateTime.now();
        int consumed = activationCredentialMapper.update(
                Wrappers.<ActivationCredentialEntity>lambdaUpdate()
                        .eq(ActivationCredentialEntity::getUserAccountId, account.getId())
                        .isNull(ActivationCredentialEntity::getConsumedAt)
                        .set(ActivationCredentialEntity::getConsumedAt, now));
        if (consumed != 1) {
            throw reissueUnavailable();
        }

        String initialCredential = credentialGenerator.generate();
        char[] credentialChars = initialCredential.toCharArray();
        String credentialHash;
        try {
            credentialHash = passwordHasher.hash(credentialChars);
        } finally {
            Arrays.fill(credentialChars, '\0');
        }
        OffsetDateTime expiresAt = now.plus(securityProperties.getActivationTtl());
        ActivationCredentialEntity replacement = new ActivationCredentialEntity();
        replacement.setUserAccountId(account.getId());
        replacement.setCredentialHash(credentialHash);
        replacement.setExpiresAt(expiresAt);
        replacement.setCreatedByAdminId(adminId);
        replacement.setCreatedAt(now);
        activationCredentialMapper.insert(replacement);

        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.ADMIN,
                adminId,
                "ACTIVATION_CREDENTIAL_REISSUED",
                "USER_ACCOUNT",
                account.getId(),
                requestId,
                "{}",
                now));
        return new ProvisionedGuestView(
                account.getId(), AccountStatus.PAID_PENDING_ACTIVATION, initialCredential, expiresAt);
    }

    private static ApiException reissueUnavailable() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "ACTIVATION_REISSUE_NOT_AVAILABLE",
                "账号当前不可补发激活凭证");
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
