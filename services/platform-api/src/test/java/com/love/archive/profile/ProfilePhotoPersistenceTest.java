package com.love.archive.profile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.love.archive.guest.domain.ProfileStatus;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProfilePhotoPersistenceTest extends ApiIntegrationTest {

    @Autowired private UserAccountMapper accountMapper;
    @Autowired private GuestProfileMapper profileMapper;

    private long profileId;

    @BeforeEach
    void seedProfile() {
        resetDatabase();
        UserAccountEntity account = new UserAccountEntity();
        account.setPhone("13800138000");
        account.setPasswordHash("not-used");
        account.setStatus(AccountStatus.ACTIVE);
        account.setMembershipTier(com.love.archive.identity.domain.MembershipTier.FREE);
        account.setMembershipCreditMinor(0L);
        account.setCreatedAt(OffsetDateTime.now());
        account.setUpdatedAt(OffsetDateTime.now());
        account.setVersion(0L);
        accountMapper.insert(account);
        GuestProfileEntity profile = new GuestProfileEntity();
        profile.setProfileNo(UUID.randomUUID());
        profile.setUserAccountId(account.getId());
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
