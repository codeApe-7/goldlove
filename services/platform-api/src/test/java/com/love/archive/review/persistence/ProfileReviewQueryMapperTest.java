package com.love.archive.review.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.guest.domain.ProfileStatus;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.review.application.DeadlineFilter;
import com.love.archive.review.application.ProfileReviewFilter;
import com.love.archive.review.domain.RevisionStatus;
import com.love.archive.review.persistence.query.ProfileRevisionFieldRow;
import com.love.archive.review.persistence.query.ProfileReviewHeaderRow;
import com.love.archive.review.persistence.query.ProfileReviewListRow;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProfileReviewQueryMapperTest extends ApiIntegrationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2030-07-01T10:15:30Z");

    @Autowired private ProfileReviewQueryMapper mapper;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private UserAccountMapper accountMapper;
    @Autowired private GuestProfileMapper profileMapper;
    @Autowired private ProfileRevisionMapper revisionMapper;
    @Autowired private ProfileRevisionFieldValueMapper revisionFieldMapper;

    private long adminId;

    @BeforeEach
    void seedAdmin() {
        resetDatabase();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("task6-query-admin");
        admin.setDisplayName("Task 6 Query Admin");
        admin.setPasswordHash("not-used-in-query-test");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(NOW);
        admin.setUpdatedAt(NOW);
        adminMapper.insert(admin);
        adminId = admin.getId();
    }

    @Test
    void filtersPendingReviewsByDeadlineAndReturnsStableOrder() {
        long overdueNear = insertProfileWithRevision(
                RevisionStatus.PENDING, NOW.minusHours(25), NOW.minusHours(1));
        long overdueFar = insertProfileWithRevision(
                RevisionStatus.PENDING, NOW.minusHours(26), NOW.minusHours(2));
        long notOverdue = insertProfileWithRevision(
                RevisionStatus.PENDING, NOW.minusHours(20), NOW.plusHours(4));
        long approved = insertProfileWithRevision(
                RevisionStatus.APPROVED, NOW.minusHours(27), NOW.minusHours(3));

        Page<ProfileReviewListRow> page = new Page<>(1, 20);
        mapper.search(page, new ProfileReviewFilter(
                RevisionStatus.PENDING, DeadlineFilter.OVERDUE, null, null, null), NOW);

        assertThat(page.getRecords())
                .extracting(ProfileReviewListRow::revisionId)
                .containsExactly(overdueFar, overdueNear);
        assertThat(mapper.count(new ProfileReviewFilter(
                        RevisionStatus.PENDING, DeadlineFilter.OVERDUE, null, null, null), NOW))
                .isEqualTo(2);
        assertThat(page.getRecords()).allMatch(row -> row.reviewDeadlineAt().isBefore(NOW));
        assertThat(page.getRecords()).isSortedAccordingTo(
                Comparator.comparing(ProfileReviewListRow::reviewDeadlineAt)
                        .thenComparing(ProfileReviewListRow::revisionId));
        assertThat(page.getRecords())
                .extracting(ProfileReviewListRow::revisionId)
                .doesNotContain(notOverdue, approved);
    }

    @Test
    void filtersByStatusSubmissionHalfOpenRangeAndProfileNumber() {
        long approvedOld = insertProfileWithRevision(
                RevisionStatus.APPROVED, NOW.minusHours(27), NOW.minusHours(3));
        long pendingMiddle = insertProfileWithRevision(
                RevisionStatus.PENDING, NOW.minusHours(26), NOW.minusHours(2));
        long pendingNew = insertProfileWithRevision(
                RevisionStatus.PENDING, NOW.minusHours(25), NOW.minusHours(1));
        UUID middleProfileNo = revisionMapper.selectById(pendingMiddle)
                .getGuestProfileId() == null
                ? null
                : profileMapper.selectById(
                                revisionMapper.selectById(pendingMiddle).getGuestProfileId())
                        .getProfileNo();

        Page<ProfileReviewListRow> page = new Page<>(1, 20);
        mapper.search(page, new ProfileReviewFilter(
                RevisionStatus.APPROVED, null, NOW.minusHours(28), NOW.minusHours(2), null), NOW);
        assertThat(page.getRecords())
                .extracting(ProfileReviewListRow::revisionId)
                .containsExactly(approvedOld);

        Page<ProfileReviewListRow> halfOpen = new Page<>(1, 20);
        mapper.search(halfOpen, new ProfileReviewFilter(
                null, null, NOW.minusHours(26), NOW.minusHours(25), null), NOW);
        assertThat(halfOpen.getRecords())
                .extracting(ProfileReviewListRow::revisionId)
                .containsExactly(pendingMiddle);

        Page<ProfileReviewListRow> byProfile = new Page<>(1, 20);
        mapper.search(byProfile, new ProfileReviewFilter(
                null, null, null, null, middleProfileNo), NOW);
        assertThat(byProfile.getRecords())
                .extracting(ProfileReviewListRow::revisionId)
                .containsExactly(pendingMiddle);
        assertThat(byProfile.getRecords()).allMatch(row ->
                row.profileNo().equals(middleProfileNo));
    }

    @Test
    void classifiesDueSoonAndOverdueAgainstServerClock() {
        long dueSoon = insertProfileWithRevision(
                RevisionStatus.PENDING, NOW.minusHours(20), NOW.plusHours(4));
        long overdue = insertProfileWithRevision(
                RevisionStatus.PENDING, NOW.minusHours(25), NOW.minusHours(1));
        long neither = insertProfileWithRevision(
                RevisionStatus.PENDING, NOW.minusHours(12), NOW.plusHours(12));

        Page<ProfileReviewListRow> dueSoonPage = new Page<>(1, 20);
        mapper.search(dueSoonPage, new ProfileReviewFilter(
                RevisionStatus.PENDING, DeadlineFilter.DUE_SOON, null, null, null), NOW);
        assertThat(dueSoonPage.getRecords())
                .extracting(ProfileReviewListRow::revisionId)
                .containsExactly(dueSoon);

        Page<ProfileReviewListRow> overduePage = new Page<>(1, 20);
        mapper.search(overduePage, new ProfileReviewFilter(
                RevisionStatus.PENDING, DeadlineFilter.OVERDUE, null, null, null), NOW);
        assertThat(overduePage.getRecords())
                .extracting(ProfileReviewListRow::revisionId)
                .containsExactly(overdue);
        assertThat(dueSoonPage.getRecords())
                .extracting(ProfileReviewListRow::revisionId)
                .doesNotContain(neither);
    }

    @Test
    void loadsDetailHeaderAndTypedRevisionFields() {
        long revisionId = insertProfileWithRevision(
                RevisionStatus.PENDING, NOW.minusHours(20), NOW.plusHours(4));
        UUID profileNo = profileMapper.selectById(
                        revisionMapper.selectById(revisionId).getGuestProfileId())
                .getProfileNo();

        ProfileRevisionFieldValueEntity field = new ProfileRevisionFieldValueEntity();
        field.setProfileRevisionId(revisionId);
        field.setFieldCode("task6_bio");
        field.setFieldLabel("自我介绍");
        field.setDataType("TEXT");
        field.setTextValue("喜欢徒步与阅读");
        field.setCreatedAt(NOW);
        revisionFieldMapper.insert(field);

        ProfileReviewHeaderRow header = mapper.detailHeader(revisionId);
        assertThat(header).isNotNull();
        assertThat(header.revisionId()).isEqualTo(revisionId);
        assertThat(header.status()).isEqualTo(RevisionStatus.PENDING);
        assertThat(header.profileNo()).isEqualTo(profileNo);
        assertThat(header.version()).isZero();
        assertThat(header.currentApprovedRevisionId()).isNull();

        List<ProfileRevisionFieldRow> fields = mapper.revisionFields(revisionId);
        assertThat(fields).hasSize(1);
        assertThat(fields.getFirst().fieldCode()).isEqualTo("task6_bio");
        assertThat(fields.getFirst().textValue()).isEqualTo("喜欢徒步与阅读");
        assertThat(fields.getFirst().profileRevisionId()).isEqualTo(revisionId);
    }

    private long insertProfileWithRevision(
            RevisionStatus status,
            OffsetDateTime submittedAt,
            OffsetDateTime reviewDeadlineAt) {
        UserAccountEntity account = new UserAccountEntity();
        account.setPhoneCiphertext(("task6-" + submittedAt.toEpochSecond())
                .getBytes(StandardCharsets.UTF_8));
        account.setPhoneHmac("task6-" + submittedAt.toEpochSecond());
        account.setPasswordHash("not-used-in-query-test");
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedByAdminId(adminId);
        account.setActivatedAt(NOW);
        account.setCreatedAt(NOW);
        account.setUpdatedAt(NOW);
        account.setVersion(0L);
        accountMapper.insert(account);

        GuestProfileEntity profile = new GuestProfileEntity();
        profile.setProfileNo(UUID.randomUUID());
        profile.setUserAccountId(account.getId());
        profile.setStatus(ProfileStatus.DRAFT);
        profile.setVersion(0L);
        profile.setCreatedAt(NOW);
        profile.setUpdatedAt(NOW);
        profileMapper.insert(profile);

        ProfileRevisionEntity revision = new ProfileRevisionEntity();
        revision.setGuestProfileId(profile.getId());
        revision.setRevisionNumber(1);
        revision.setStatus(status);
        revision.setSubmittedByAccountId(account.getId());
        revision.setSubmittedAt(submittedAt);
        revision.setReviewDeadlineAt(reviewDeadlineAt);
        revision.setSubmissionKeyHmac("task6-key-" + UUID.randomUUID());
        revision.setRequestPayloadSha256("a".repeat(64));
        revision.setVersion(0L);
        revision.setCreatedAt(submittedAt);
        revisionMapper.insert(revision);
        return revision.getId();
    }
}
