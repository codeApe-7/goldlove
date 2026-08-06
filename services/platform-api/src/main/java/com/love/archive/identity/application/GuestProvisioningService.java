package com.love.archive.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.audit.domain.AuditActorType;
import com.love.archive.audit.persistence.AuditLogEntity;
import com.love.archive.audit.persistence.AuditLogMapper;
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
import com.love.archive.payment.domain.PaymentStatus;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.PaymentRecordMapper;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestProvisioningService {

    private final UserAccountMapper userAccountMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ActivationCredentialMapper activationCredentialMapper;
    private final AuditLogMapper auditLogMapper;
    private final PhoneNormalizer phoneNormalizer;
    private final PhoneProtector phoneProtector;
    private final InitialCredentialGenerator credentialGenerator;
    private final PasswordHasher passwordHasher;
    private final IdentitySecurityProperties securityProperties;

    public GuestProvisioningService(
            UserAccountMapper userAccountMapper,
            PaymentRecordMapper paymentRecordMapper,
            ActivationCredentialMapper activationCredentialMapper,
            AuditLogMapper auditLogMapper,
            PhoneNormalizer phoneNormalizer,
            PhoneProtector phoneProtector,
            InitialCredentialGenerator credentialGenerator,
            PasswordHasher passwordHasher,
            IdentitySecurityProperties securityProperties) {
        this.userAccountMapper = userAccountMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.activationCredentialMapper = activationCredentialMapper;
        this.auditLogMapper = auditLogMapper;
        this.phoneNormalizer = phoneNormalizer;
        this.phoneProtector = phoneProtector;
        this.credentialGenerator = credentialGenerator;
        this.passwordHasher = passwordHasher;
        this.securityProperties = securityProperties;
    }

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

        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setUserAccountId(account.getId());
        payment.setPaymentReference(paymentReference);
        payment.setAmountMinor(amountMinor);
        payment.setCurrency("CNY");
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(paidAt);
        payment.setOperatorAdminId(adminId);
        payment.setNote(note);
        payment.setCreatedAt(now);
        try {
            paymentRecordMapper.insert(payment);
        } catch (DataIntegrityViolationException exception) {
            if (containsConstraint(exception, "uq_payment_record_reference")) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_REFERENCE_EXISTS", "支付流水号已存在");
            }
            throw exception;
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

        AuditLogEntity audit = new AuditLogEntity();
        audit.setActorType(AuditActorType.ADMIN);
        audit.setActorId(adminId);
        audit.setAction("GUEST_ACCOUNT_PROVISIONED");
        audit.setTargetType("USER_ACCOUNT");
        audit.setTargetId(account.getId());
        audit.setRequestId(requestId);
        audit.setMetadata("{\"paymentReference\":\"" + paymentReference + "\"}");
        audit.setOccurredAt(now);
        auditLogMapper.insert(audit);

        return new ProvisionedGuestView(
                account.getId(), AccountStatus.PAID_PENDING_ACTIVATION, initialCredential, expiresAt);
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
