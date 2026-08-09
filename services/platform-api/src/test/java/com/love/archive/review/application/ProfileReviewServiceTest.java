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
import com.love.archive.common.web.PageView;
import com.love.archive.consent.persistence.AuthorizationDocumentEntity;
import com.love.archive.consent.persistence.AuthorizationDocumentMapper;
import com.love.archive.consent.persistence.AuthorizationRecordEntity;
import com.love.archive.consent.persistence.AuthorizationRecordMapper;
import com.love.archive.guest.application.GuestProfileDraftService;
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
import com.love.archive.review.domain.ReviewResult;
import com.love.archive.review.domain.RevisionStatus;
import com.love.archive.review.persistence.ProfileReviewRecordEntity;
import com.love.archive.review.persistence.ProfileReviewRecordMapper;
import com.love.archive.review.persistence.ProfileRevisionEntity;
import com.love.archive.review.persistence.ProfileRevisionMapper;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(ProfileReviewServiceTest.FixedClockConfiguration.class)
class ProfileReviewServiceTest extends ApiIntegrationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2030-07-01T10:15:30Z");
    private static final String REQUEST_ID = "req-task-6";
    private static final String DYNAMIC_FIELD_CODE = "task6_bio";

    @Autowired private ProfileReviewService service;
    @Autowired private ProfileSubmissionService submissionService;
    @Autowired private GuestProfileDraftService draftService;
    @Autowired private GuestProfileMapper profileMapper;
    @Autowired private ProfileFieldDefinitionMapper definitionMapper;
    @Autowired private ProfilePhotoMapper photoMapper;
    @Autowired private ProfileRevisionMapper revisionMapper;
    @Autowired private ProfileReviewRecordMapper reviewRecordMapper;
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
        accountId = insertAccount(AccountStatus.ACTIVE, "task6-primary");
        authorizationDocumentId = currentAuthorizationDocument().getId();
        ensureDynamicDefinition(false);
        when(storageService.signDownloadUrl(anyString(), any()))
                .thenReturn("https://loveplatform-1314980040.cos.ap-guangzhou.myqcloud.com/signed");
    }

    @Test
    void approvingSwitchesPointerButRejectingPreservesLastApprovedPointer() {
        long firstApproved = submitPending("review-pointer-approve");
        service.approve(adminId, firstApproved, 0L, REQUEST_ID);
        assertThat(profile().getCurrentApprovedRevisionId()).isEqualTo(firstApproved);
        assertThat(profile().getStatus()).isEqualTo(ProfileStatus.APPROVED);

        long secondPending = submitChangedDraft("review-pointer-reject");
        service.reject(
                adminId, secondPending, 0L, "CONTENT_INCOMPLETE", "请补充职业信息", REQUEST_ID);

        assertThat(profile().getCurrentApprovedRevisionId()).isEqualTo(firstApproved);
        assertThat(profile().getPendingRevisionId()).isNull();
        assertThat(profile().getStatus()).isEqualTo(ProfileStatus.CHANGES_REQUESTED);
        assertThat(revisionMapper.selectById(secondPending).getStatus())
                .isEqualTo(RevisionStatus.REJECTED);
    }

    @Test
    void capsReviewPageSizeAtOneHundred() {
        PageView<ProfileReviewListItem> page = service.search(
                new ProfileReviewFilter(null, null, null, null, null), 1, 500);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(100);
        assertThat(page.total()).isZero();
    }

    @Test
    void returnsTypedDifferencesAgainstLastApprovedRevision() {
        long firstApproved = submitPending("review-diff-first");
        service.approve(adminId, firstApproved, 0L, REQUEST_ID);
        long secondPending = submitChangedDraft("review-diff-second");

        ProfileReviewDetail detail = service.detail(secondPending);

        assertThat(detail.lastApprovedRevisionId()).isEqualTo(firstApproved);
        assertThat(detail.differences())
                .extracting(ProfileFieldDifference::fieldCode)
                .contains("city", DYNAMIC_FIELD_CODE);
        ProfileFieldDifference city = detail.differences().stream()
                .filter(difference -> difference.fieldCode().equals("city"))
                .findFirst()
                .orElseThrow();
        assertThat(city.oldValue()).isEqualTo("杭州");
        assertThat(city.newValue()).isEqualTo("宁波");
        ProfileFieldDifference bio = detail.differences().stream()
                .filter(difference -> difference.fieldCode().equals(DYNAMIC_FIELD_CODE))
                .findFirst()
                .orElseThrow();
        assertThat(bio.oldValue()).isEqualTo("喜欢徒步");
        assertThat(bio.newValue()).isEqualTo("喜欢跑步");
    }

    @Test
    void detailReturnsSnapshottedPhotos() {
        long revisionId = submitPending("review-photo-detail");

        ProfileReviewDetail detail = service.detail(revisionId);

        assertThat(detail.photos()).hasSize(1);
        assertThat(detail.photos().getFirst().category()).isEqualTo("AVATAR");
    }

    @Test
    void appendsReviewRecordAndAuditForApproveAndReject() {
        long first = submitPending("review-record-approve");
        ProfileReviewDecisionView approved = service.approve(adminId, first, 0L, REQUEST_ID);
        assertThat(approved.status()).isEqualTo(RevisionStatus.APPROVED);
        assertThat(reviewRecordMapper.selectOne(Wrappers.<ProfileReviewRecordEntity>lambdaQuery()
                        .eq(ProfileReviewRecordEntity::getProfileRevisionId, first))
                .getResult()).isEqualTo(ReviewResult.APPROVED);
        assertThat(auditMapper.selectCount(Wrappers.<AuditLogEntity>lambdaQuery()
                        .eq(AuditLogEntity::getAction, "PROFILE_APPROVED")
                        .eq(AuditLogEntity::getActorId, adminId)
                        .eq(AuditLogEntity::getTargetId, first)))
                .isEqualTo(1);

        long second = submitChangedDraft("review-record-reject");
        service.reject(adminId, second, 0L, "CONTENT_INCOMPLETE", "请补充职业信息", REQUEST_ID);
        ProfileReviewRecordEntity rejected = reviewRecordMapper.selectOne(
                Wrappers.<ProfileReviewRecordEntity>lambdaQuery()
                        .eq(ProfileReviewRecordEntity::getProfileRevisionId, second));
        assertThat(rejected.getResult()).isEqualTo(ReviewResult.REJECTED);
        assertThat(rejected.getReasonCode()).isEqualTo("CONTENT_INCOMPLETE");
        assertThat(rejected.getComment()).isEqualTo("请补充职业信息");
        assertThat(auditMapper.selectCount(Wrappers.<AuditLogEntity>lambdaQuery()
                        .eq(AuditLogEntity::getAction, "PROFILE_REJECTED")
                        .eq(AuditLogEntity::getActorId, adminId)
                        .eq(AuditLogEntity::getTargetId, second)))
                .isEqualTo(1);
    }

    @Test
    void requiresGuestFacingRejectComment() {
        long revisionId = submitPending("review-comment");

        assertCode(() -> service.reject(
                        adminId, revisionId, 0L, "CONTENT_INCOMPLETE", null, REQUEST_ID),
                "FIELD_VALUE_INVALID");
        assertCode(() -> service.reject(
                        adminId, revisionId, 0L, "CONTENT_INCOMPLETE", "   ", REQUEST_ID),
                "FIELD_VALUE_INVALID");
        assertCode(() -> service.reject(
                        adminId, revisionId, 0L, "CONTENT_INCOMPLETE",
                        "a".repeat(1001), REQUEST_ID),
                "FIELD_VALUE_INVALID");

        assertThat(reviewRecordMapper.selectCount(Wrappers.lambdaQuery())).isZero();
        assertThat(revisionMapper.selectById(revisionId).getStatus())
                .isEqualTo(RevisionStatus.PENDING);
    }

    @Test
    void rejectsStaleExpectedRevisionVersion() {
        long revisionId = submitPending("review-stale-version");

        assertCode(() -> service.approve(adminId, revisionId, 1L, REQUEST_ID),
                "PROFILE_VERSION_CONFLICT");
        assertThat(revisionMapper.selectById(revisionId).getStatus())
                .isEqualTo(RevisionStatus.PENDING);
    }

    @Test
    void repeatsIdenticalCompletedDecisionIdempotently() {
        long revisionId = submitPending("review-idempotent-approve");
        ProfileReviewDecisionView first = service.approve(adminId, revisionId, 0L, REQUEST_ID);
        ProfileReviewDecisionView replay = service.approve(adminId, revisionId, 0L, REQUEST_ID);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.status()).isEqualTo(RevisionStatus.APPROVED);
        assertThat(reviewRecordMapper.selectCount(Wrappers.lambdaQuery())).isEqualTo(1);

        long second = submitChangedDraft("review-idempotent-reject");
        ProfileReviewDecisionView rejectedFirst =
                service.reject(adminId, second, 0L, "CONTENT_INCOMPLETE", "请补充", REQUEST_ID);
        ProfileReviewDecisionView rejectedReplay =
                service.reject(adminId, second, 0L, "CONTENT_INCOMPLETE", "请补充", REQUEST_ID);
        assertThat(rejectedReplay.id()).isEqualTo(rejectedFirst.id());
        assertThat(rejectedReplay.status()).isEqualTo(RevisionStatus.REJECTED);
        assertThat(reviewRecordMapper.selectCount(Wrappers.lambdaQuery())).isEqualTo(2);
    }

    @Test
    void rejectsConflictingSecondDecision() {
        long revisionId = submitPending("review-conflict");
        service.approve(adminId, revisionId, 0L, REQUEST_ID);

        assertCode(() -> service.reject(
                        adminId, revisionId, 0L, "CONTENT_INCOMPLETE", "请补充", REQUEST_ID),
                "PROFILE_REVIEW_ALREADY_COMPLETED");
    }

    @Test
    void rejectsDetailAndDecisionForUnknownRevision() {
        assertCode(() -> service.detail(999_999L), "PROFILE_REVISION_NOT_FOUND");
        assertCode(() -> service.approve(adminId, 999_999L, 0L, REQUEST_ID),
                "PROFILE_REVISION_NOT_FOUND");
    }

    private long submitPending(String key) {
        saveCompleteDraft();
        recordPaidAuthorization(authorizationDocumentId);
        recordConsent(authorizationDocumentId, NOW.plusDays(1));
        return submissionService.submit(accountId, key, REQUEST_ID).id();
    }

    private long submitChangedDraft(String key) {
        GuestProfileEntity current = profile();
        draftService.save(accountId, new SaveGuestProfileCommand(
                current.getVersion(),
                "男",
                LocalDate.of(1995, 5, 20),
                178,
                "本科",
                "工程师",
                "20-30万",
                "宁波",
                "wx-task6-private",
                "dy-task6-private",
                "task6 private nickname",
                URI.create("https://www.douyin.com/user/task6-private"),
                List.of(new TextFieldInput(DYNAMIC_FIELD_CODE, "喜欢跑步"))),
                REQUEST_ID);
        return submissionService.submit(accountId, key, REQUEST_ID).id();
    }

    private void saveCompleteDraft() {
        draftService.save(accountId, new SaveGuestProfileCommand(
                null,
                "男",
                LocalDate.of(1995, 5, 20),
                178,
                "本科",
                "工程师",
                "20-30万",
                "杭州",
                "wx-task6-private",
                "dy-task6-private",
                "task6 private nickname",
                URI.create("https://www.douyin.com/user/task6-private"),
                List.of(new TextFieldInput(DYNAMIC_FIELD_CODE, "喜欢徒步"))),
                REQUEST_ID);
        insertAvatarPhoto(profile().getId());
    }

    private void insertAvatarPhoto(long profileId) {
        ProfilePhotoEntity photo = new ProfilePhotoEntity();
        photo.setGuestProfileId(profileId);
        photo.setCategory(PhotoCategory.AVATAR);
        photo.setObjectKey("profiles/" + profileId + "/avatar/fixture.jpg");
        photo.setSha256("e".repeat(64));
        photo.setSizeBytes(10L);
        photo.setContentType("image/jpeg");
        photo.setWidth(100);
        photo.setHeight(100);
        photo.setSortOrder(0);
        OffsetDateTime now = OffsetDateTime.now();
        photo.setCreatedAt(now);
        photo.setUpdatedAt(now);
        photoMapper.insert(photo);
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
        payment.setPaymentReference("PAY-TASK6-" + paymentMapper.selectCount(Wrappers.lambdaQuery()));
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
                "consent:session", "task6-session"));
        record.setCreatedAt(effectiveAt);
        authorizationRecordMapper.insert(record);
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
        admin.setUsername("task6-admin");
        admin.setDisplayName("Task 6 Admin");
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

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedProfileReviewClock() {
            return Clock.fixed(Instant.parse("2030-07-01T10:15:30Z"), ZoneOffset.UTC);
        }
    }
}
