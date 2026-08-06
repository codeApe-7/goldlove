package com.love.archive.consent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.audit.persistence.AuditLogMapper;
import com.love.archive.common.web.ApiException;
import com.love.archive.consent.domain.AuthorizationDocumentStatus;
import com.love.archive.consent.persistence.AuthorizationDocumentEntity;
import com.love.archive.consent.persistence.AuthorizationDocumentMapper;
import com.love.archive.consent.persistence.AuthorizationRecordEntity;
import com.love.archive.consent.persistence.AuthorizationRecordMapper;
import com.love.archive.identity.application.GuestProvisioningService;
import com.love.archive.identity.web.ProvisionedGuestView;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(ConsentServiceTest.FixedClockConfiguration.class)
class ConsentServiceTest extends ApiIntegrationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2030-07-01T10:15:30Z");

    @Autowired private ConsentService service;
    @Autowired private ConsentEligibility eligibility;
    @Autowired private AuthorizationDocumentMapper documentMapper;
    @Autowired private AuthorizationRecordMapper recordMapper;
    @Autowired private AuditLogMapper auditLogMapper;
    @Autowired private AdminUserMapper adminUserMapper;
    @Autowired private GuestProvisioningService provisioningService;

    private long accountId;
    private long authorizationDocumentId;
    private long adminId;

    @BeforeEach
    void preparePaidGuest() {
        resetDatabase();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("consent-service-admin");
        admin.setDisplayName("Consent Service Admin");
        admin.setPasswordHash("$argon2id$test-placeholder");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(NOW);
        admin.setUpdatedAt(NOW);
        adminUserMapper.insert(admin);
        adminId = admin.getId();

        ProvisionedGuestView provisioned = provisioningService.provision(
                adminId,
                "13800138000",
                "PAY-CONSENT-SERVICE",
                199_00L,
                NOW.minusMinutes(5),
                "v0.3",
                null,
                "consent-service-test");
        accountId = provisioned.accountId();
        authorizationDocumentId = document("v0.3").getId();
    }

    @Test
    void recordsServerTimedOneYearConsentForThePaidVersion() {
        ConsentView consent = service.accept(accountId, new ConsentEvidenceCommand(
                "v0.3", true, "guest-activation", "203.0.113.8", "Mozilla/5.0", "token-value"));

        assertThat(consent.effectiveAt()).isEqualTo(consent.acceptedAt());
        assertThat(consent.expiresAt()).isEqualTo(consent.effectiveAt().plusYears(1));
        AuthorizationRecordEntity stored = recordMapper.selectById(consent.id());
        assertThat(stored.getClientIpHmac()).doesNotContain("203.0.113.8");
        assertThat(stored.getSessionReferenceHmac()).doesNotContain("token-value");
        assertThat(stored.getUserAgentSha256()).doesNotContain("Mozilla/5.0");
        assertThat(auditLogMapper.selectCount(Wrappers.lambdaQuery(
                        com.love.archive.audit.persistence.AuditLogEntity.class)
                .eq(com.love.archive.audit.persistence.AuditLogEntity::getAction, "CONSENT_ACCEPTED")
                .eq(com.love.archive.audit.persistence.AuditLogEntity::getTargetId, consent.id())))
                .isEqualTo(1);
    }

    @Test
    void rejectsAcceptedFalseAtValidationBoundary() {
        assertThatThrownBy(() -> service.accept(accountId, new ConsentEvidenceCommand(
                        "v0.3", false, "guest-activation", "203.0.113.8", "Mozilla/5.0", "token-value")))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("CONSENT_ACCEPTANCE_REQUIRED");
    }

    @Test
    void rejectsSourcePageOutsideWhitelist() {
        assertThatThrownBy(() -> service.accept(accountId, new ConsentEvidenceCommand(
                        "v0.3", true, "admin-console", "203.0.113.8", "Mozilla/5.0", "token-value")))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("CONSENT_SOURCE_PAGE_INVALID");
    }

    @Test
    void rejectsDocumentDifferentFromPaidEvidence() throws SQLException {
        retireV03AndActivate("v0.4");

        assertThatThrownBy(() -> service.accept(accountId, new ConsentEvidenceCommand(
                        "v0.4", true, "guest-profile", "203.0.113.8", "Mozilla/5.0", "token-value")))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("PREPAYMENT_AUTHORIZATION_EVIDENCE_MISSING");
    }

    @Test
    void rejectsRetiredDocumentNotReferencedByOwnPayment() throws SQLException {
        insertDocument("v0.2", AuthorizationDocumentStatus.RETIRED);

        assertThatThrownBy(() -> service.accept(accountId, new ConsentEvidenceCommand(
                        "v0.2", true, "guest-profile", "203.0.113.8", "Mozilla/5.0", "token-value")))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("AUTHORIZATION_DOCUMENT_NOT_ACTIVE");
    }

    @Test
    void returnsExistingUnexpiredConsentForRepeatedAcceptance() {
        ConsentView first = service.accept(accountId, new ConsentEvidenceCommand(
                "v0.3", true, "guest-consent", "203.0.113.8", "Mozilla/5.0", "token-value"));

        ConsentView replay = service.accept(accountId, new ConsentEvidenceCommand(
                "v0.3", true, "guest-consent", "198.51.100.22", "Other browser", "other-token"));

        assertThat(replay).isEqualTo(first);
        assertThat(recordMapper.selectCount(Wrappers.<AuthorizationRecordEntity>lambdaQuery()
                        .eq(AuthorizationRecordEntity::getUserAccountId, accountId)
                        .eq(AuthorizationRecordEntity::getAuthorizationDocumentId, authorizationDocumentId)))
                .isEqualTo(1);
    }

    @Test
    void treatsConsentAsExpiredAtExactExpiryInstant() {
        AuthorizationRecordEntity expired = new AuthorizationRecordEntity();
        expired.setUserAccountId(accountId);
        expired.setAuthorizationDocumentId(authorizationDocumentId);
        expired.setAccepted(true);
        expired.setAcceptedAt(NOW.minusYears(1));
        expired.setEffectiveAt(NOW.minusYears(1));
        expired.setExpiresAt(NOW);
        expired.setSourcePage("guest-consent");
        expired.setClientIpHmac("expired-ip-hmac");
        expired.setUserAgentSha256("a".repeat(64));
        expired.setSessionReferenceHmac("expired-session-hmac");
        recordMapper.insert(expired);

        assertThat(service.current(accountId)).isEmpty();
        assertThatThrownBy(() -> eligibility.requireValid(accountId, authorizationDocumentId, NOW))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("CONSENT_EXPIRED");
    }

    private AuthorizationDocumentEntity document(String version) {
        return documentMapper.selectOne(Wrappers.<AuthorizationDocumentEntity>lambdaQuery()
                .eq(AuthorizationDocumentEntity::getDocumentCode, "PAID_PROFILE_LIVE_CONTENT")
                .eq(AuthorizationDocumentEntity::getVersion, version));
    }

    private void retireV03AndActivate(String version) throws SQLException {
        try (Connection owner = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement retire = owner.prepareStatement("""
                        UPDATE authorization_document
                        SET status = 'RETIRED'
                        WHERE document_code = 'PAID_PROFILE_LIVE_CONTENT' AND version = 'v0.3'
                        """)) {
            retire.executeUpdate();
        }
        insertDocument(version, AuthorizationDocumentStatus.ACTIVE);
    }

    private void insertDocument(String version, AuthorizationDocumentStatus status) throws SQLException {
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
            insert.setString(2, status.name());
            insert.executeUpdate();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedConsentClock() {
            return Clock.fixed(Instant.parse("2030-07-01T10:15:30Z"), ZoneOffset.UTC);
        }
    }
}
