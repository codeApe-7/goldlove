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
import com.love.archive.identity.persistence.ActivationCredentialEntity;
import com.love.archive.identity.persistence.ActivationCredentialMapper;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.identity.web.GuestSessionView;
import com.love.archive.identity.web.ProvisionedGuestView;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class GuestAuthServiceTest extends ApiIntegrationTest {

    @Autowired private GuestProvisioningService provisioningService;
    @Autowired private GuestAuthService guestAuthService;
    @Autowired private AdminUserMapper adminUserMapper;
    @Autowired private UserAccountMapper userAccountMapper;
    @Autowired private ActivationCredentialMapper activationCredentialMapper;
    @Autowired private AuditLogMapper auditLogMapper;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private JdbcClient jdbcClient;

    private Long adminId;

    @BeforeEach
    void cleanAndCreateAdmin() {
        jdbcClient.sql("""
                TRUNCATE TABLE audit_log, activation_credential, payment_record,
                    external_identity, user_account, admin_user RESTART IDENTITY CASCADE
                """).update();
        OffsetDateTime now = OffsetDateTime.now();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("guest-auth-admin");
        admin.setDisplayName("Guest Auth Admin");
        admin.setPasswordHash("$argon2id$test-placeholder");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
    }

    @Test
    void activatesAccountAndConsumesCredentialAtomically() {
        ProvisionedGuestView provisioned = provision("13800138000", "PAY-ACTIVATE-001");

        GuestSessionView activated = guestAuthService.activate(
                "13800138000",
                provisioned.initialCredential(),
                "New-password-2026",
                "activation-request");

        UserAccountEntity account = userAccountMapper.selectById(provisioned.accountId());
        ActivationCredentialEntity credential = activationCredentialMapper.selectOne(
                Wrappers.<ActivationCredentialEntity>lambdaQuery()
                        .eq(ActivationCredentialEntity::getUserAccountId, provisioned.accountId()));
        assertThat(activated.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(passwordHasher.matches("New-password-2026".toCharArray(), account.getPasswordHash())).isTrue();
        assertThat(credential.getConsumedAt()).isNotNull();
        assertThat(auditLogMapper.selectCount(null)).isEqualTo(2);
    }

    @Test
    void wrongCredentialLeavesAccountPendingAndCredentialUnused() {
        ProvisionedGuestView provisioned = provision("13800138000", "PAY-ACTIVATE-002");

        assertThatThrownBy(() -> guestAuthService.activate(
                "13800138000", "wrong-initial-credential", "New-password-2026", "wrong-request"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("ACTIVATION_INVALID_CREDENTIAL"));

        assertThat(userAccountMapper.selectById(provisioned.accountId()).getStatus())
                .isEqualTo(AccountStatus.PAID_PENDING_ACTIVATION);
        assertThat(activationCredentialMapper.selectOne(
                        Wrappers.<ActivationCredentialEntity>lambdaQuery()
                                .eq(ActivationCredentialEntity::getUserAccountId, provisioned.accountId()))
                .getConsumedAt()).isNull();
    }

    @Test
    void rejectsExpiredUsedAndWeakActivationAttempts() {
        ProvisionedGuestView expired = provision("13800138000", "PAY-ACTIVATE-003");
        jdbcClient.sql("""
                UPDATE activation_credential
                SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 days',
                    expires_at = CURRENT_TIMESTAMP - INTERVAL '1 day'
                WHERE user_account_id = :accountId
                """).param("accountId", expired.accountId()).update();

        assertActivationError(
                "13800138000", expired.initialCredential(), "New-password-2026", "ACTIVATION_CREDENTIAL_EXPIRED");

        ProvisionedGuestView used = provision("13900139000", "PAY-ACTIVATE-004");
        guestAuthService.activate(
                "13900139000", used.initialCredential(), "New-password-2026", "first-activation");
        assertActivationError(
                "13900139000", used.initialCredential(), "Another-password-2026", "ACTIVATION_NOT_AVAILABLE");

        ProvisionedGuestView weak = provision("13700137000", "PAY-ACTIVATE-005");
        assertActivationError(
                "13700137000", weak.initialCredential(), "123456", "PASSWORD_POLICY_VIOLATION");
    }

    private void assertActivationError(String phone, String credential, String password, String code) {
        assertThatThrownBy(() -> guestAuthService.activate(phone, credential, password, "failed-activation"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }

    private ProvisionedGuestView provision(String phone, String paymentReference) {
        return provisioningService.provision(
                adminId,
                phone,
                paymentReference,
                199_00L,
                OffsetDateTime.now().minusMinutes(5),
                null,
                "guest-auth-service-test");
    }
}
