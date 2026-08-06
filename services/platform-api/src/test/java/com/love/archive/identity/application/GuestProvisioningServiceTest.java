package com.love.archive.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.audit.persistence.AuditLogMapper;
import com.love.archive.common.web.ApiException;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.domain.PhoneNormalizer;
import com.love.archive.identity.persistence.ActivationCredentialEntity;
import com.love.archive.identity.persistence.ActivationCredentialMapper;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.identity.security.PhoneProtector;
import com.love.archive.identity.web.ProvisionedGuestView;
import com.love.archive.payment.persistence.PaymentRecordMapper;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GuestProvisioningServiceTest extends ApiIntegrationTest {

    @Autowired private GuestProvisioningService service;
    @Autowired private AdminUserMapper adminUserMapper;
    @Autowired private UserAccountMapper userAccountMapper;
    @Autowired private ActivationCredentialMapper activationCredentialMapper;
    @Autowired private PaymentRecordMapper paymentRecordMapper;
    @Autowired private AuditLogMapper auditLogMapper;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private PhoneProtector phoneProtector;
    @Autowired private PhoneNormalizer phoneNormalizer;

    private Long adminId;

    @BeforeEach
    void cleanAndCreateAdmin() {
        resetDatabase();

        OffsetDateTime now = OffsetDateTime.now();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("provisioning-admin");
        admin.setDisplayName("Provisioning Admin");
        admin.setPasswordHash("$argon2id$test-placeholder");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
    }

    @Test
    void atomicallyCreatesPaidAccountCredentialAndAuditWithoutPlaintextSecrets() {
        ProvisionedGuestView view = provision("138 0013 8000", "PAY-20260806-001");

        UserAccountEntity account = userAccountMapper.selectById(view.accountId());
        ActivationCredentialEntity credential = activationCredentialMapper.selectOne(
                Wrappers.<ActivationCredentialEntity>lambdaQuery()
                        .eq(ActivationCredentialEntity::getUserAccountId, view.accountId()));

        assertThat(account.getStatus()).isEqualTo(AccountStatus.PAID_PENDING_ACTIVATION);
        assertThat(phoneProtector.decrypt(account.getPhoneCiphertext())).isEqualTo("13800138000");
        assertThat(account.getPhoneHmac()).isEqualTo(phoneProtector.searchHash("13800138000"));
        assertThat(account.getPasswordHash()).isNull();
        assertThat(passwordHasher.matches(view.initialCredential().toCharArray(), credential.getCredentialHash()))
                .isTrue();
        assertThat(credential.getCredentialHash()).doesNotContain(view.initialCredential());
        assertThat(paymentRecordMapper.selectCount(null)).isOne();
        assertThat(auditLogMapper.selectCount(null)).isOne();
    }

    @Test
    void rejectsDuplicatePhone() {
        provision("13800138000", "PAY-20260806-002");

        assertThatThrownBy(() -> provision("+86 138-0013-8000", "PAY-20260806-003"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("ACCOUNT_ALREADY_EXISTS"));
    }

    @Test
    void duplicatePaymentReferenceRollsBackNewAccount() {
        provision("13800138000", "PAY-DUPLICATE");

        assertThatThrownBy(() -> provision("13900139000", "PAY-DUPLICATE"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("PAYMENT_REFERENCE_EXISTS"));

        String secondPhoneHash = phoneProtector.searchHash(phoneNormalizer.normalize("13900139000"));
        assertThat(userAccountMapper.selectCount(
                Wrappers.<UserAccountEntity>lambdaQuery().eq(UserAccountEntity::getPhoneHmac, secondPhoneHash)))
                .isZero();
        assertThat(paymentRecordMapper.selectCount(null)).isOne();
        assertThat(activationCredentialMapper.selectCount(null)).isOne();
        assertThat(auditLogMapper.selectCount(null)).isOne();
    }

    @Test
    void reissuesActivationCredentialAndInvalidatesThePreviousHashAtomically() {
        ProvisionedGuestView original = provision("13800138000", "PAY-REISSUE");

        ProvisionedGuestView reissued = service.reissueActivationCredential(
                adminId, "138 0013 8000", "reissue-request");

        List<ActivationCredentialEntity> credentials = activationCredentialMapper.selectList(
                Wrappers.<ActivationCredentialEntity>lambdaQuery()
                        .eq(ActivationCredentialEntity::getUserAccountId, original.accountId())
                        .orderByAsc(ActivationCredentialEntity::getId));
        assertThat(reissued.accountId()).isEqualTo(original.accountId());
        assertThat(reissued.initialCredential()).isNotEqualTo(original.initialCredential());
        assertThat(credentials).hasSize(2);
        assertThat(credentials.getFirst().getConsumedAt()).isNotNull();
        assertThat(credentials.getLast().getConsumedAt()).isNull();
        assertThat(passwordHasher.matches(
                reissued.initialCredential().toCharArray(), credentials.getLast().getCredentialHash())).isTrue();
        assertThat(auditLogMapper.selectCount(null)).isEqualTo(2);
    }

    private ProvisionedGuestView provision(String phone, String paymentReference) {
        return service.provision(
                adminId,
                phone,
                paymentReference,
                199_00L,
                OffsetDateTime.now().minusMinutes(5),
                "线下付款",
                "provisioning-service-test");
    }
}
