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
import com.love.archive.identity.domain.MembershipTier;
import com.love.archive.identity.domain.PhoneNormalizer;
import com.love.archive.identity.domain.RegistrationChannel;
import com.love.archive.identity.persistence.ActivationCredentialEntity;
import com.love.archive.identity.persistence.ActivationCredentialMapper;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.identity.security.PhoneProtector;
import com.love.archive.identity.web.ProvisionedGuestView;
import com.love.archive.payment.application.PaymentAuthorizationEvidence;
import com.love.archive.payment.domain.PaymentChannelType;
import com.love.archive.payment.domain.PaymentStatus;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.PaymentRecordMapper;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
    @Autowired private PaymentAuthorizationEvidence paymentAuthorizationEvidence;
    @Autowired private AuditLogMapper auditLogMapper;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private PhoneProtector phoneProtector;
    @Autowired private PhoneNormalizer phoneNormalizer;

    private Long adminId;

    @BeforeEach
    void cleanAndCreateAdmin() {
        resetDatabase();
        resetAuthorizationDocuments();

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
    void provisioningRequiresAndPersistsTheActivePresentedVersion() {
        ProvisionedGuestView result = service.provision(
                adminId,
                "13800138000",
                "PAY-AUTHORIZATION-EVIDENCE",
                199_00L,
                OffsetDateTime.now().minusMinutes(5),
                "v0.3",
                "线下付款",
                "authorization-evidence-test");

        PaymentRecordEntity payment = paymentRecordMapper.selectOne(
                Wrappers.<PaymentRecordEntity>lambdaQuery()
                        .eq(PaymentRecordEntity::getUserAccountId, result.accountId()));

        assertThat(payment.getPresentedAuthorizationDocumentId()).isNotNull();
    }

    @Test
    void rejectsUnknownAuthorizationVersionWithDocumentNotFound() {
        assertThatThrownBy(() -> service.provision(
                adminId,
                "13800138000",
                "PAY-UNKNOWN-AUTHORIZATION",
                199_00L,
                OffsetDateTime.now().minusMinutes(5),
                "v9.9",
                "线下付款",
                "unknown-authorization-test"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AUTHORIZATION_DOCUMENT_NOT_FOUND"));
    }

    @Test
    void rejectsDraftAuthorizationVersionWithDocumentNotActive() throws SQLException {
        insertAuthorizationDocument("draft-v1", "DRAFT");

        assertThatThrownBy(() -> service.provision(
                adminId,
                "13800138000",
                "PAY-DRAFT-AUTHORIZATION",
                199_00L,
                OffsetDateTime.now().minusMinutes(5),
                "draft-v1",
                "线下付款",
                "draft-authorization-test"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AUTHORIZATION_DOCUMENT_NOT_ACTIVE"));
    }

    @Test
    void reportsMissingEvidenceForHistoricalNullPaymentVersion() {
        UserAccountEntity account = new UserAccountEntity();
        account.setPhoneCiphertext(new byte[] {1, 2, 3});
        account.setPhoneHmac("historical-payment-without-document");
        account.setStatus(AccountStatus.PAID_PENDING_ACTIVATION);
        account.setCreatedByAdminId(adminId);
        account.setCreatedAt(OffsetDateTime.now());
        account.setUpdatedAt(OffsetDateTime.now());
        userAccountMapper.insert(account);

        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setUserAccountId(account.getId());
        payment.setPaymentReference("PAY-HISTORICAL-NULL-AUTHORIZATION");
        payment.setAmountMinor(199_00L);
        payment.setCurrency("CNY");
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(OffsetDateTime.now().minusMinutes(5));
        payment.setOperatorAdminId(adminId);
        payment.setCreatedAt(OffsetDateTime.now());
        paymentRecordMapper.insert(payment);

        assertThat(paymentAuthorizationEvidence.findPaidAuthorization(account.getId())).isEmpty();
    }

    @Test
    void manualProvisioningStartsAtVipAndCreditsTheRegisteredAmount() {
        ProvisionedGuestView view = provision("13800138000", "PAY-MEMBERSHIP-VIP");

        UserAccountEntity account = userAccountMapper.selectById(view.accountId());
        PaymentRecordEntity payment = paymentRecordMapper.selectOne(
                Wrappers.<PaymentRecordEntity>lambdaQuery()
                        .eq(PaymentRecordEntity::getUserAccountId, view.accountId()));

        assertThat(account.getRegistrationChannel()).isEqualTo(RegistrationChannel.ADMIN_MANUAL);
        assertThat(account.getMembershipTier()).isEqualTo(MembershipTier.VIP);
        assertThat(account.getMembershipCreditMinor()).isEqualTo(199_00L);
        assertThat(payment.getPaymentChannel()).isEqualTo(PaymentChannelType.MANUAL);
        assertThat(payment.getMembershipCreditMinor()).isEqualTo(199_00L);
        assertThat(payment.getOutTradeNo()).isNull();
    }

    @Test
    void manualProvisioningReachingTheThresholdUpgradesToSvip() {
        ProvisionedGuestView view = service.provision(
                adminId,
                "13800138000",
                "PAY-MEMBERSHIP-SVIP",
                599_00L,
                OffsetDateTime.now().minusMinutes(5),
                "v0.3",
                "线下付款",
                "membership-svip-test");

        UserAccountEntity account = userAccountMapper.selectById(view.accountId());
        assertThat(account.getMembershipCreditMinor()).isEqualTo(599_00L);
        assertThat(account.getMembershipTier()).isEqualTo(MembershipTier.SVIP);
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
                "v0.3",
                "线下付款",
                "provisioning-service-test");
    }

    private void resetAuthorizationDocuments() {
        try (Connection owner = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement delete = owner.prepareStatement("""
                        DELETE FROM authorization_document
                        WHERE document_code = 'PAID_PROFILE_LIVE_CONTENT' AND version <> 'v0.3'
                        """);
                PreparedStatement activate = owner.prepareStatement("""
                        UPDATE authorization_document
                        SET status = 'ACTIVE'
                        WHERE document_code = 'PAID_PROFILE_LIVE_CONTENT' AND version = 'v0.3'
                        """)) {
            delete.executeUpdate();
            activate.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("测试授权文档重置失败", exception);
        }
    }

    private void insertAuthorizationDocument(String version, String status) throws SQLException {
        try (Connection owner = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement insert = owner.prepareStatement("""
                        INSERT INTO authorization_document
                            (document_code, version, title, content, content_sha256, status, effective_at)
                        VALUES (
                            'PAID_PROFILE_LIVE_CONTENT', ?, '测试授权书', '测试授权书内容',
                            encode(digest(convert_to('测试授权书内容', 'UTF8'), 'sha256'), 'hex'),
                            ?, CURRENT_TIMESTAMP
                        )
                        """)) {
            insert.setString(1, version);
            insert.setString(2, status);
            insert.executeUpdate();
        }
    }
}
