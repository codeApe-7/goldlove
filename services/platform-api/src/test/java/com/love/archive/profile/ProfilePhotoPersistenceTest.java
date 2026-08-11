package com.love.archive.profile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.guest.domain.ProfileStatus;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.review.domain.RevisionStatus;
import com.love.archive.review.persistence.ProfileRevisionEntity;
import com.love.archive.review.persistence.ProfileRevisionMapper;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProfilePhotoPersistenceTest extends ApiIntegrationTest {

    @Autowired private AdminUserMapper adminMapper;
    @Autowired private UserAccountMapper accountMapper;
    @Autowired private GuestProfileMapper profileMapper;
    @Autowired private ProfileRevisionMapper revisionMapper;

    private long profileId;
    private long accountId;

    @BeforeEach
    void seedProfile() {
        resetDatabase();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("photo-persistence-admin");
        admin.setDisplayName("Photo Persistence Admin");
        admin.setPasswordHash("not-used");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(OffsetDateTime.now());
        admin.setUpdatedAt(OffsetDateTime.now());
        adminMapper.insert(admin);

        UserAccountEntity account = new UserAccountEntity();
        account.setPhoneCiphertext("photo-persistence".getBytes(StandardCharsets.UTF_8));
        account.setPhoneHmac("photo-persistence");
        account.setPasswordHash("not-used");
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedByAdminId(admin.getId());
        account.setActivatedAt(OffsetDateTime.now());
        account.setCreatedAt(OffsetDateTime.now());
        account.setUpdatedAt(OffsetDateTime.now());
        account.setVersion(0L);
        accountMapper.insert(account);
        accountId = account.getId();

        GuestProfileEntity profile = new GuestProfileEntity();
        profile.setProfileNo(UUID.randomUUID());
        profile.setUserAccountId(accountId);
        profile.setStatus(ProfileStatus.DRAFT);
        profile.setVersion(0L);
        profile.setCreatedAt(OffsetDateTime.now());
        profile.setUpdatedAt(OffsetDateTime.now());
        profileMapper.insert(profile);
        profileId = profile.getId();
    }

    @Test
    void enforcesOneAvatarAndLifeSortUniqueness() {
        insertPhoto("AVATAR", "profiles/1/avatar/a.jpg", 0);
        assertThatThrownBy(() -> insertPhoto("AVATAR", "profiles/1/avatar/b.jpg", 1))
                .hasMessageContaining("uq_profile_photo_avatar_one");

        insertPhoto("LIFE", "profiles/1/life/c.jpg", 0);
        assertThatThrownBy(() -> insertPhoto("LIFE", "profiles/1/life/d.jpg", 0))
                .hasMessageContaining("uq_profile_photo_profile_category_sort");
    }

    @Test
    void rejectsInvalidCategory() {
        assertThatThrownBy(() -> execute("""
                INSERT INTO profile_photo (
                    guest_profile_id, category, object_key, sort_order
                ) VALUES (?, 'VIDEO', 'profiles/1/video/v.mp4', 0)
                """, profileId))
                .hasMessageContaining("ck_profile_photo_category");
    }

    @Test
    void revisionPhotoRowsAreImmutable() {
        long revisionId = insertRevision();
        execute("""
                INSERT INTO profile_revision_photo (
                    profile_revision_id, category, object_key, sort_order, created_at
                ) VALUES (?, 'AVATAR', 'profiles/1/avatar/snap.jpg', 0, ?)
                """, revisionId, OffsetDateTime.now());

        assertThatThrownBy(() -> execute("""
                UPDATE profile_revision_photo SET sort_order = 50 WHERE profile_revision_id = ?
                """, revisionId))
                .hasMessageContaining("profile_revision_photo rows are immutable");
        assertThatThrownBy(() -> execute("""
                DELETE FROM profile_revision_photo WHERE profile_revision_id = ?
                """, revisionId))
                .hasMessageContaining("profile_revision_photo rows are immutable");
    }

    private long insertRevision() {
        OffsetDateTime now = OffsetDateTime.now();
        ProfileRevisionEntity revision = new ProfileRevisionEntity();
        revision.setGuestProfileId(profileId);
        revision.setRevisionNumber(1);
        revision.setStatus(RevisionStatus.PENDING);
        revision.setSubmittedByAccountId(accountId);
        revision.setSubmittedAt(now);
        revision.setReviewDeadlineAt(now.plusHours(24));
        revision.setSubmissionKeyHmac(UUID.randomUUID().toString());
        revision.setRequestPayloadSha256("c".repeat(64));
        revision.setVersion(0L);
        revision.setCreatedAt(now);
        revisionMapper.insert(revision);
        return revision.getId();
    }

    private void insertPhoto(String category, String objectKey, int sortOrder) {
        execute("""
                INSERT INTO profile_photo (
                    guest_profile_id, category, object_key, sort_order
                ) VALUES (?, ?, ?, ?)
                """, profileId, category, objectKey, sortOrder);
    }

    private static void execute(String sql, Object... args) {
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
