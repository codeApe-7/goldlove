package com.love.archive.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.audit.persistence.AuditLogEntity;
import com.love.archive.audit.persistence.AuditLogMapper;
import com.love.archive.common.security.SensitiveValueProtector;
import com.love.archive.common.web.ApiException;
import com.love.archive.consent.persistence.AuthorizationDocumentEntity;
import com.love.archive.consent.persistence.AuthorizationDocumentMapper;
import com.love.archive.consent.persistence.AuthorizationRecordEntity;
import com.love.archive.consent.persistence.AuthorizationRecordMapper;
import com.love.archive.guest.application.GuestProfileDraftService;
import com.love.archive.guest.application.ProfilePhotoTarget;
import com.love.archive.guest.application.SaveGuestProfileCommand;
import com.love.archive.guest.application.TextFieldInput;
import com.love.archive.guest.domain.FieldStorageKind;
import com.love.archive.guest.domain.PhotoCategory;
import com.love.archive.guest.domain.ProfileFieldType;
import com.love.archive.guest.domain.ProfileStatus;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.guest.persistence.ProfileFieldDefinitionEntity;
import com.love.archive.guest.persistence.ProfileFieldDefinitionMapper;
import com.love.archive.guest.persistence.ProfilePhotoEntity;
import com.love.archive.guest.persistence.ProfilePhotoMapper;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.payment.domain.PaymentStatus;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.PaymentRecordMapper;
import com.love.archive.review.domain.RevisionStatus;
import com.love.archive.review.persistence.ProfileRevisionEntity;
import com.love.archive.review.persistence.ProfileRevisionFieldValueEntity;
import com.love.archive.review.persistence.ProfileRevisionFieldValueMapper;
import com.love.archive.review.persistence.ProfileRevisionMapper;
import com.love.archive.review.persistence.ProfileRevisionPhotoEntity;
import com.love.archive.review.persistence.ProfileRevisionPhotoMapper;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(ProfileSubmissionServiceTest.FixedClockConfiguration.class)
class ProfileSubmissionServiceTest extends ApiIntegrationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2030-07-01T10:15:30Z");
    private static final String REQUEST_ID = "req-task-5";
    private static final String DYNAMIC_FIELD_CODE = "task5_bio";
    private static final String FIXTURE_AVATAR_UUID = "00000000-0000-0000-0000-000000000001";

    @Autowired private ProfileSubmissionService service;
    @Autowired private GuestProfileDraftService draftService;
    @Autowired private GuestProfileMapper profileMapper;
    @Autowired private ProfileFieldDefinitionMapper definitionMapper;
    @Autowired private ProfileRevisionMapper revisionMapper;
    @Autowired private ProfileRevisionFieldValueMapper revisionFieldMapper;
    @Autowired private ProfilePhotoMapper photoMapper;
    @Autowired private ProfileRevisionPhotoMapper revisionPhotoMapper;
    @Autowired private AuthorizationDocumentMapper documentMapper;
    @Autowired private AuthorizationRecordMapper authorizationRecordMapper;
    @Autowired private PaymentRecordMapper paymentMapper;
    @Autowired private UserAccountMapper accountMapper;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private AuditLogMapper auditMapper;
    @Autowired private SensitiveValueProtector sensitiveValueProtector;
    @MockitoBean private ObjectStorageService storageService;

    private long adminId;
    private long accountId;
    private long authorizationDocumentId;

    @BeforeEach
    void prepareDraftOwner() {
        resetDatabase();
        adminId = insertAdmin();
        accountId = insertAccount(AccountStatus.ACTIVE, "task5-primary");
        authorizationDocumentId = currentAuthorizationDocument().getId();
        ensureDynamicDefinition(false);
        when(storageService.signDownloadUrl(anyString(), any()))
                .thenReturn("https://loveplatform-1314980040.cos.ap-guangzhou.myqcloud.com/signed");
    }

    @Test
    void createsACompleteImmutableSnapshotAndDeadline() {
        saveCompleteDraft();
        recordPaidAuthorization(authorizationDocumentId);
        recordConsent(authorizationDocumentId, NOW.plusDays(1));

        ProfileRevisionView revision = service.submit(accountId, "submit-001", REQUEST_ID);

        assertThat(revision.status()).isEqualTo(RevisionStatus.PENDING);
        assertThat(revision.reviewDeadlineAt()).isEqualTo(revision.submittedAt().plusHours(24));
        ProfileRevisionEntity stored = revisionMapper.selectById(revision.id());
        assertThat(stored.getRevisionNumber()).isEqualTo(1);
        assertThat(stored.getGender()).isEqualTo("男");
        assertThat(stored.getBirthDate()).isEqualTo(LocalDate.of(1995, 5, 20));
        assertThat(stored.getWechatIdCiphertext()).isNotNull();
        assertThat(stored.getWechatIdHmac()).isNotBlank();
        assertThat(stored.getRequestPayloadSha256()).hasSize(64);
        assertThat(revisionFields(revision.id()))
                .extracting(ProfileRevisionFieldValueEntity::getFieldCode)
                .containsExactly(DYNAMIC_FIELD_CODE);
        assertThat(profile().getStatus()).isEqualTo(ProfileStatus.PENDING_REVIEW);
        assertThat(profile().getPendingRevisionId()).isEqualTo(revision.id());
        assertThat(auditMapper.selectCount(Wrappers.<AuditLogEntity>lambdaQuery()
                        .eq(AuditLogEntity::getAction, "PROFILE_SUBMITTED")
                        .eq(AuditLogEntity::getActorId, accountId)
                        .eq(AuditLogEntity::getTargetId, revision.id())))
                .isEqualTo(1);
    }

    @Test
    void retriesSameKeyButRejectsKeyReusedForDifferentDraft() {
        saveCompleteDraft();
        recordPaidAuthorization(authorizationDocumentId);
        recordConsent(authorizationDocumentId, NOW.plusDays(1));
        ProfileRevisionView first = service.submit(accountId, "submit-002", REQUEST_ID);

        assertThat(service.submit(accountId, "submit-002", REQUEST_ID).id())
                .isEqualTo(first.id());

        profileMapper.update(Wrappers.<GuestProfileEntity>lambdaUpdate()
                .eq(GuestProfileEntity::getId, profile().getId())
                .set(GuestProfileEntity::getCity, "宁波"));

        assertCode(() -> service.submit(accountId, "submit-002", REQUEST_ID),
                "IDEMPOTENCY_KEY_REUSED");
        assertThat(revisionMapper.selectCount(Wrappers.lambdaQuery())).isEqualTo(1);
    }

    @Test
    void replaysSameKeyAfterNoOpFullSaveRotatesEveryProtectedCiphertext() throws SQLException {
        saveCompleteDraft();
        recordPaidAuthorization(authorizationDocumentId);
        recordConsent(authorizationDocumentId, NOW.plusDays(1));
        ProfileRevisionView first = service.submit(
                accountId, "submit-protected-no-op", REQUEST_ID);
        GuestProfileEntity beforeResave = profile();
        byte[] firstWechatCiphertext = beforeResave.getWechatIdCiphertext();
        byte[] firstDouyinCiphertext = beforeResave.getDouyinIdCiphertext();
        byte[] firstNicknameCiphertext = beforeResave.getDouyinNicknameCiphertext();
        byte[] firstProfileUrlCiphertext = beforeResave.getDouyinProfileUrlCiphertext();
        completeRevision(first.id());

        draftService.save(accountId, completeCommand(2L, null), REQUEST_ID);

        GuestProfileEntity resaved = profile();
        assertThat(resaved.getWechatIdCiphertext()).isNotEqualTo(firstWechatCiphertext);
        assertThat(resaved.getDouyinIdCiphertext()).isNotEqualTo(firstDouyinCiphertext);
        assertThat(resaved.getDouyinNicknameCiphertext()).isNotEqualTo(firstNicknameCiphertext);
        assertThat(resaved.getDouyinProfileUrlCiphertext()).isNotEqualTo(firstProfileUrlCiphertext);
        assertThat(service.submit(accountId, "submit-protected-no-op", REQUEST_ID).id())
                .isEqualTo(first.id());
    }

    @ParameterizedTest
    @EnumSource(ProtectedField.class)
    void rejectsSameKeyWhenAnyProtectedPlaintextChanges(ProtectedField changedField)
            throws SQLException {
        saveCompleteDraft();
        recordPaidAuthorization(authorizationDocumentId);
        recordConsent(authorizationDocumentId, NOW.plusDays(1));
        ProfileRevisionView first = service.submit(
                accountId, "submit-protected-change", REQUEST_ID);
        completeRevision(first.id());

        draftService.save(accountId, completeCommand(2L, changedField), REQUEST_ID);

        assertCode(() -> service.submit(
                        accountId, "submit-protected-change", REQUEST_ID),
                "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void rejectsMissingConsent() {
        saveCompleteDraft();
        recordPaidAuthorization(authorizationDocumentId);

        assertCode(() -> service.submit(accountId, "submit-no-consent", REQUEST_ID),
                "CONSENT_REQUIRED");
    }

    @Test
    void rejectsConsentAtOrAfterExpiry() {
        saveCompleteDraft();
        recordPaidAuthorization(authorizationDocumentId);
        recordConsent(authorizationDocumentId, NOW);

        assertCode(() -> service.submit(accountId, "submit-expired", REQUEST_ID),
                "CONSENT_EXPIRED");
    }

    @Test
    void rejectsHistoricalPaymentWithoutPresentedDocument() {
        saveCompleteDraft();
        recordPaidAuthorization(null);
        recordConsent(authorizationDocumentId, NOW.plusDays(1));

        assertCode(() -> service.submit(accountId, "submit-historical-payment", REQUEST_ID),
                "PREPAYMENT_AUTHORIZATION_EVIDENCE_MISSING");
    }

    @Test
    void rejectsConsentForDifferentDocument() throws SQLException {
        saveCompleteDraft();
        recordPaidAuthorization(authorizationDocumentId);
        long otherDocumentId = insertAuthorizationDocument("v-task5-other");
        recordConsent(otherDocumentId, NOW.plusDays(1));

        assertCode(() -> service.submit(accountId, "submit-wrong-consent", REQUEST_ID),
                "CONSENT_REQUIRED");
    }

    @Test
    void rejectsMissingRequiredCoreAndDynamicFields() {
        ensureDynamicDefinition(true);
        try {
            draftService.save(accountId, new SaveGuestProfileCommand(
                    null, null, null, null, null, null, null, null,
                    null, null, null, null, List.of()), REQUEST_ID);
            recordPaidAuthorization(authorizationDocumentId);
            recordConsent(authorizationDocumentId, NOW.plusDays(1));

            assertThatThrownBy(() -> service.submit(
                            accountId, "submit-incomplete", REQUEST_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(Throwable::getMessage)
                    .asString()
                    .contains("gender", "birth_date", "height_cm", "education",
                            "occupation", "income_range", "city", DYNAMIC_FIELD_CODE);
        } finally {
            ensureDynamicDefinition(false);
        }
    }

    @Test
    void rejectsSubmissionWithoutAvatarPhoto() {
        draftService.save(accountId, completeCommand(null, null), REQUEST_ID);
        recordPaidAuthorization(authorizationDocumentId);
        recordConsent(authorizationDocumentId, NOW.plusDays(1));

        assertCode(() -> service.submit(accountId, "submit-no-avatar", REQUEST_ID),
                "PROFILE_VALIDATION_FAILED");
    }

    @Test
    void snapshotsCurrentPhotosIntoImmutableRevision() {
        saveCompleteDraft();
        recordPaidAuthorization(authorizationDocumentId);
        recordConsent(authorizationDocumentId, NOW.plusDays(1));

        ProfileRevisionView revision = service.submit(
                accountId, "submit-photo", REQUEST_ID);

        assertThat(revision.photos()).hasSize(1);
        ProfileRevisionView.Photo photoView = revision.photos().getFirst();
        assertThat(photoView.category()).isEqualTo("AVATAR");
        assertThat(photoView.objectKey())
                .isEqualTo("profiles/" + profile().getId() + "/avatar/"
                        + FIXTURE_AVATAR_UUID + ".jpg");
        assertThat(photoView.sortOrder()).isZero();
        assertThat(photoView.downloadUrl())
                .startsWith("https://loveplatform-1314980040");
        assertThat(revisionPhotoMapper.selectCount(
                        Wrappers.<ProfileRevisionPhotoEntity>lambdaQuery()
                                .eq(ProfileRevisionPhotoEntity::getProfileRevisionId,
                                        revision.id())))
                .isEqualTo(1);
        ProfileRevisionPhotoEntity snapshot = revisionPhotoMapper.selectOne(
                Wrappers.<ProfileRevisionPhotoEntity>lambdaQuery()
                        .eq(ProfileRevisionPhotoEntity::getProfileRevisionId, revision.id()));
        assertThat(snapshot.getObjectKey())
                .isEqualTo("profiles/" + profile().getId() + "/avatar/"
                        + FIXTURE_AVATAR_UUID + ".jpg");
        assertThat(snapshot.getSortOrder()).isZero();
    }

    @Test
    void rejectsSecondPendingRevision() {
        saveCompleteDraft();
        recordPaidAuthorization(authorizationDocumentId);
        recordConsent(authorizationDocumentId, NOW.plusDays(1));
        service.submit(accountId, "submit-pending-first", REQUEST_ID);

        assertCode(() -> service.submit(accountId, "submit-pending-second", REQUEST_ID),
                "PROFILE_REVIEW_IN_PROGRESS");
        assertThat(revisionMapper.selectCount(Wrappers.lambdaQuery())).isEqualTo(1);
    }

    @Test
    void incrementsRevisionNumbersPerProfile() throws SQLException {
        saveCompleteDraft();
        recordPaidAuthorization(authorizationDocumentId);
        recordConsent(authorizationDocumentId, NOW.plusDays(1));
        ProfileRevisionView first = service.submit(accountId, "submit-version-1", REQUEST_ID);
        completeAndEditDraft(first.id());

        ProfileRevisionView second = service.submit(accountId, "submit-version-2", REQUEST_ID);

        assertThat(first.revisionNumber()).isEqualTo(1);
        assertThat(second.revisionNumber()).isEqualTo(2);
        assertThat(second.id()).isNotEqualTo(first.id());
    }

    @Test
    void rollsBackRevisionWhenDynamicSnapshotInsertFails() throws SQLException {
        saveCompleteDraft();
        recordPaidAuthorization(authorizationDocumentId);
        recordConsent(authorizationDocumentId, NOW.plusDays(1));
        installRevisionFieldInsertFailure();
        try {
            assertThatThrownBy(() -> service.submit(
                            accountId, "submit-rollback", REQUEST_ID))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            removeRevisionFieldInsertFailure();
        }

        assertThat(revisionMapper.selectCount(Wrappers.lambdaQuery())).isZero();
        assertThat(revisionFieldMapper.selectCount(Wrappers.lambdaQuery())).isZero();
        assertThat(profile().getStatus()).isEqualTo(ProfileStatus.DRAFT);
        assertThat(profile().getPendingRevisionId()).isNull();
        assertThat(auditMapper.selectCount(Wrappers.<AuditLogEntity>lambdaQuery()
                        .eq(AuditLogEntity::getAction, "PROFILE_SUBMITTED")
                        .eq(AuditLogEntity::getActorId, accountId)))
                .isZero();
    }

    private void saveCompleteDraft() {
        draftService.save(accountId, completeCommand(null, null), REQUEST_ID);
        insertAvatarPhoto(profile().getId());
    }

    private void insertAvatarPhoto(long profileId) {
        ProfilePhotoEntity photo = new ProfilePhotoEntity();
        photo.setGuestProfileId(profileId);
        photo.setCategory(PhotoCategory.AVATAR);
        photo.setObjectKey("profiles/" + profileId + "/avatar/"
                + FIXTURE_AVATAR_UUID + ".jpg");
        photo.setSortOrder(0);
        OffsetDateTime now = OffsetDateTime.now();
        photo.setCreatedAt(now);
        photo.setUpdatedAt(now);
        photoMapper.insert(photo);
    }

    private SaveGuestProfileCommand completeCommand(
            Long expectedVersion,
            ProtectedField changedField) {
        String wechatId = changedField == ProtectedField.WECHAT_ID
                ? "wx-task5-changed" : "wx-task5-private";
        String douyinId = changedField == ProtectedField.DOUYIN_ID
                ? "dy-task5-changed" : "dy-task5-private";
        String nickname = changedField == ProtectedField.DOUYIN_NICKNAME
                ? "task5 changed nickname" : "task5 private nickname";
        URI profileUrl = changedField == ProtectedField.DOUYIN_PROFILE_URL
                ? URI.create("https://www.douyin.com/user/task5-changed")
                : URI.create("https://www.douyin.com/user/task5-private");
        return new SaveGuestProfileCommand(
                expectedVersion,
                "男",
                LocalDate.of(1995, 5, 20),
                178,
                "本科",
                "工程师",
                "20-30万",
                "杭州",
                wechatId,
                douyinId,
                nickname,
                profileUrl,
                List.of(new TextFieldInput(DYNAMIC_FIELD_CODE, "喜欢徒步")),
                new ProfilePhotoTarget(
                        expectedVersion == null ? null
                                : "profiles/" + profile().getId() + "/avatar/"
                                        + FIXTURE_AVATAR_UUID + ".jpg",
                        List.of()));
    }

    private void ensureDynamicDefinition(boolean required) {
        ProfileFieldDefinitionEntity definition = definitionMapper.selectOne(
                Wrappers.<ProfileFieldDefinitionEntity>lambdaQuery()
                        .eq(ProfileFieldDefinitionEntity::getFieldCode, DYNAMIC_FIELD_CODE));
        if (definition == null) {
            OffsetDateTime now = OffsetDateTime.now();
            definition = new ProfileFieldDefinitionEntity();
            definition.setFieldCode(DYNAMIC_FIELD_CODE);
            definition.setLabel("自我介绍");
            definition.setStorageKind(FieldStorageKind.DYNAMIC);
            definition.setDataType(ProfileFieldType.TEXT);
            definition.setRequired(required);
            definition.setEnabled(true);
            definition.setEverUsed(false);
            definition.setSortOrder(500);
            definition.setVersion(0L);
            definition.setCreatedAt(now);
            definition.setUpdatedAt(now);
            definitionMapper.insert(definition);
            return;
        }
        definitionMapper.update(Wrappers.<ProfileFieldDefinitionEntity>lambdaUpdate()
                .eq(ProfileFieldDefinitionEntity::getId, definition.getId())
                .set(ProfileFieldDefinitionEntity::getRequired, required)
                .set(ProfileFieldDefinitionEntity::getEnabled, true));
    }

    private void recordPaidAuthorization(Long documentId) {
        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setUserAccountId(accountId);
        payment.setPaymentReference("PAY-TASK5-" + paymentMapper.selectCount(Wrappers.lambdaQuery()));
        payment.setAmountMinor(199_00L);
        payment.setCurrency("CNY");
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(NOW.minusMinutes(5));
        payment.setOperatorAdminId(adminId);
        payment.setPresentedAuthorizationDocumentId(documentId);
        payment.setCreatedAt(NOW.minusMinutes(5));
        paymentMapper.insert(payment);
    }

    private void recordConsent(long documentId, OffsetDateTime expiresAt) {
        OffsetDateTime effectiveAt = expiresAt.minusYears(1);
        AuthorizationRecordEntity record = new AuthorizationRecordEntity();
        record.setUserAccountId(accountId);
        record.setAuthorizationDocumentId(documentId);
        record.setAccepted(true);
        record.setAcceptedAt(effectiveAt);
        record.setEffectiveAt(effectiveAt);
        record.setExpiresAt(expiresAt);
        record.setSourcePage("guest-profile");
        record.setClientIpHmac(sensitiveValueProtector.hmac(
                "consent:ip", "198.51.100.23"));
        record.setUserAgentSha256("a".repeat(64));
        record.setSessionReferenceHmac(sensitiveValueProtector.hmac(
                "consent:session", "task5-session"));
        record.setCreatedAt(effectiveAt);
        authorizationRecordMapper.insert(record);
    }

    private List<ProfileRevisionFieldValueEntity> revisionFields(long revisionId) {
        return revisionFieldMapper.selectList(
                Wrappers.<ProfileRevisionFieldValueEntity>lambdaQuery()
                        .eq(ProfileRevisionFieldValueEntity::getProfileRevisionId, revisionId)
                        .orderByAsc(ProfileRevisionFieldValueEntity::getFieldCode));
    }

    private GuestProfileEntity profile() {
        return profileMapper.selectOne(Wrappers.<GuestProfileEntity>lambdaQuery()
                .eq(GuestProfileEntity::getUserAccountId, accountId));
    }

    private AuthorizationDocumentEntity currentAuthorizationDocument() {
        return documentMapper.selectOne(Wrappers.<AuthorizationDocumentEntity>lambdaQuery()
                .eq(AuthorizationDocumentEntity::getDocumentCode, "PAID_PROFILE_LIVE_CONTENT")
                .eq(AuthorizationDocumentEntity::getVersion, "v0.3"));
    }

    private long insertAdmin() {
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("task5-admin");
        admin.setDisplayName("Task 5 Admin");
        admin.setPasswordHash("not-used-in-service-test");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(NOW);
        admin.setUpdatedAt(NOW);
        adminMapper.insert(admin);
        return admin.getId();
    }

    private long insertAccount(AccountStatus status, String seed) {
        UserAccountEntity account = new UserAccountEntity();
        account.setPhoneCiphertext(seed.getBytes(StandardCharsets.UTF_8));
        account.setPhoneHmac(seed);
        account.setPasswordHash("not-used-in-service-test");
        account.setStatus(status);
        account.setCreatedByAdminId(adminId);
        account.setActivatedAt(NOW);
        account.setCreatedAt(NOW);
        account.setUpdatedAt(NOW);
        account.setVersion(0L);
        accountMapper.insert(account);
        return account.getId();
    }

    private long insertAuthorizationDocument(String version) throws SQLException {
        try (Connection connection = ownerConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO authorization_document (
                            document_code, version, title, content, content_sha256,
                            status, effective_at
                        ) VALUES (
                            'PAID_PROFILE_LIVE_CONTENT', ?, 'Task 5 historical document',
                            'Task 5 historical content',
                            encode(digest(convert_to('Task 5 historical content', 'UTF8'), 'sha256'), 'hex'),
                            'RETIRED', ?
                        )
                        RETURNING id
                        """)) {
            statement.setString(1, version);
            statement.setObject(2, NOW.minusYears(1));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private void completeAndEditDraft(long revisionId) throws SQLException {
        try (Connection connection = ownerConnection();
                PreparedStatement complete = connection.prepareStatement("""
                        UPDATE profile_revision
                        SET status = 'APPROVED', reviewed_at = ?, version = version + 1
                        WHERE id = ?
                        """);
                PreparedStatement edit = connection.prepareStatement("""
                        UPDATE guest_profile
                        SET pending_revision_id = NULL,
                            current_approved_revision_id = ?,
                            status = 'DRAFT',
                            city = '宁波',
                            version = version + 1,
                            updated_at = ?
                        WHERE user_account_id = ?
                        """)) {
            complete.setObject(1, NOW);
            complete.setLong(2, revisionId);
            complete.executeUpdate();
            edit.setLong(1, revisionId);
            edit.setObject(2, NOW);
            edit.setLong(3, accountId);
            edit.executeUpdate();
        }
    }

    private void completeRevision(long revisionId) throws SQLException {
        try (Connection connection = ownerConnection();
                PreparedStatement complete = connection.prepareStatement("""
                        UPDATE profile_revision
                        SET status = 'APPROVED', reviewed_at = ?, version = version + 1
                        WHERE id = ?
                        """);
                PreparedStatement approve = connection.prepareStatement("""
                        UPDATE guest_profile
                        SET pending_revision_id = NULL,
                            current_approved_revision_id = ?,
                            status = 'APPROVED',
                            version = version + 1,
                            updated_at = ?
                        WHERE user_account_id = ?
                        """)) {
            complete.setObject(1, NOW);
            complete.setLong(2, revisionId);
            complete.executeUpdate();
            approve.setLong(1, revisionId);
            approve.setObject(2, NOW);
            approve.setLong(3, accountId);
            approve.executeUpdate();
        }
    }

    private void installRevisionFieldInsertFailure() throws SQLException {
        try (Connection connection = ownerConnection();
                PreparedStatement function = connection.prepareStatement("""
                        CREATE OR REPLACE FUNCTION fail_task5_revision_field_insert()
                        RETURNS trigger AS $$
                        BEGIN
                            RAISE EXCEPTION 'task5 forced revision field failure';
                        END;
                        $$ LANGUAGE plpgsql
                        """);
                PreparedStatement trigger = connection.prepareStatement("""
                        CREATE TRIGGER fail_task5_revision_field_insert
                        BEFORE INSERT ON profile_revision_field_value
                        FOR EACH ROW EXECUTE FUNCTION fail_task5_revision_field_insert()
                        """)) {
            function.executeUpdate();
            trigger.executeUpdate();
        }
    }

    private void removeRevisionFieldInsertFailure() throws SQLException {
        try (Connection connection = ownerConnection();
                PreparedStatement trigger = connection.prepareStatement("""
                        DROP TRIGGER IF EXISTS fail_task5_revision_field_insert
                        ON profile_revision_field_value
                        """);
                PreparedStatement function = connection.prepareStatement("""
                        DROP FUNCTION IF EXISTS fail_task5_revision_field_insert()
                        """)) {
            trigger.executeUpdate();
            function.executeUpdate();
        }
    }

    private static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void assertCode(ThrowingOperation operation, String expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }

    private enum ProtectedField {
        WECHAT_ID,
        DOUYIN_ID,
        DOUYIN_NICKNAME,
        DOUYIN_PROFILE_URL
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedProfileSubmissionClock() {
            return Clock.fixed(Instant.parse("2030-07-01T10:15:30Z"), ZoneOffset.UTC);
        }
    }
}
