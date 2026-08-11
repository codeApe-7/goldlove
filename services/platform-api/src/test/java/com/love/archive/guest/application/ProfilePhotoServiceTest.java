package com.love.archive.guest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.common.web.ApiException;
import com.love.archive.guest.domain.PhotoCategory;
import com.love.archive.guest.domain.ProfileStatus;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.guest.persistence.ProfilePhotoEntity;
import com.love.archive.guest.persistence.ProfilePhotoMapper;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.review.domain.RevisionStatus;
import com.love.archive.review.persistence.ProfileRevisionEntity;
import com.love.archive.review.persistence.ProfileRevisionMapper;
import com.love.archive.review.persistence.ProfileRevisionPhotoEntity;
import com.love.archive.review.persistence.ProfileRevisionPhotoMapper;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.storage.application.StoredObjectView;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ProfilePhotoServiceTest extends ApiIntegrationTest {

    @Autowired private ProfilePhotoService photoService;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private UserAccountMapper accountMapper;
    @Autowired private GuestProfileMapper profileMapper;
    @Autowired private ProfilePhotoMapper photoMapper;
    @Autowired private ProfileRevisionMapper revisionMapper;
    @Autowired private ProfileRevisionPhotoMapper revisionPhotoMapper;
    @MockitoBean private ObjectStorageService storageService;

    private long accountId;
    private long profileId;

    @BeforeEach
    void seedDraftOwner() {
        resetDatabase();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("photo-service-admin");
        admin.setDisplayName("Photo Service Admin");
        admin.setPasswordHash("not-used");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(OffsetDateTime.now());
        admin.setUpdatedAt(OffsetDateTime.now());
        adminMapper.insert(admin);

        accountId = insertAccount("photo-service");

        GuestProfileEntity profile = new GuestProfileEntity();
        profile.setProfileNo(UUID.randomUUID());
        profile.setUserAccountId(accountId);
        profile.setStatus(ProfileStatus.DRAFT);
        profile.setVersion(0L);
        profile.setCreatedAt(OffsetDateTime.now());
        profile.setUpdatedAt(OffsetDateTime.now());
        profileMapper.insert(profile);
        profileId = profile.getId();

        when(storageService.put(anyString(), any(byte[].class), anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    byte[] bytes = invocation.getArgument(1);
                    String type = invocation.getArgument(2);
                    return new StoredObjectView(
                            key, "loveplatform-1314980040", bytes.length, type, "a".repeat(64));
                });
        when(storageService.signDownloadUrl(anyString(), any()))
                .thenReturn("https://loveplatform-1314980040.cos.ap-guangzhou.myqcloud.com/signed");
    }

    @Test
    void uploadStoresAvatarAndReturnsViewWithSignedUrl() throws Exception {
        ProfilePhotoView view = photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());

        assertThat(view.category()).isEqualTo("AVATAR");
        assertThat(view.objectKey()).startsWith("profiles/" + accountId + "/avatar/");
        assertThat(view.downloadUrl()).startsWith("https://loveplatform-1314980040");
        ProfilePhotoEntity stored = photoMapper.selectOne(
                Wrappers.<ProfilePhotoEntity>lambdaQuery()
                        .eq(ProfilePhotoEntity::getGuestProfileId, profileId));
        assertThat(stored.getObjectKey()).startsWith("profiles/" + accountId + "/avatar/");
        assertThat(stored.getSortOrder()).isZero();
    }

    @Test
    void avatarUploadReplacesExistingAvatarAndLifeStillCapsAtSix() throws Exception {
        ProfilePhotoView first = photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());

        ProfilePhotoView second = photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());

        assertThat(second.id()).isNotEqualTo(first.id());
        List<ProfilePhotoEntity> avatars = photoMapper.selectList(
                Wrappers.<ProfilePhotoEntity>lambdaQuery()
                        .eq(ProfilePhotoEntity::getGuestProfileId, profileId)
                        .eq(ProfilePhotoEntity::getCategory, PhotoCategory.AVATAR));
        assertThat(avatars).hasSize(1);
        assertThat(avatars.getFirst().getId()).isEqualTo(second.id());
        verify(storageService).delete(anyString());

        for (int i = 0; i < 6; i++) {
            photoService.upload(accountId, PhotoCategory.LIFE, imageBytes());
        }
        assertCode(() -> photoService.upload(accountId, PhotoCategory.LIFE, imageBytes()),
                "PHOTO_COUNT_LIMIT_EXCEEDED");
    }

    @Test
    void avatarReplacementKeepsOldObjectWhenSnapshotted() throws Exception {
        ProfilePhotoView first = photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());
        ProfilePhotoEntity oldPhoto = photoMapper.selectById(first.id());
        insertRevisionSnapshot(oldPhoto.getObjectKey());

        ProfilePhotoView second = photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());

        verify(storageService, never()).delete(anyString());
        List<ProfilePhotoEntity> avatars = photoMapper.selectList(
                Wrappers.<ProfilePhotoEntity>lambdaQuery()
                        .eq(ProfilePhotoEntity::getGuestProfileId, profileId)
                        .eq(ProfilePhotoEntity::getCategory, PhotoCategory.AVATAR));
        assertThat(avatars).hasSize(1);
        assertThat(avatars.getFirst().getId()).isEqualTo(second.id());
    }

    @Test
    void rejectsUploadOrDeleteWhenPendingReview() throws Exception {
        profileMapper.update(Wrappers.<GuestProfileEntity>lambdaUpdate()
                .eq(GuestProfileEntity::getId, profileId)
                .set(GuestProfileEntity::getStatus, ProfileStatus.PENDING_REVIEW));

        assertCode(() -> photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes()),
                "PHOTO_NOT_EDITABLE");
        assertCode(() -> photoService.delete(accountId, 1L), "PHOTO_NOT_EDITABLE");
    }

    @Test
    void rejectsUploadBeforeDraftExists() throws Exception {
        deleteProfileWithOwner(profileId);
        assertCode(() -> photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes()),
                "PROFILE_NOT_STARTED");
    }

    @Test
    void deleteRemovesRowAndObjectWhenNotSnapshotted() throws Exception {
        ProfilePhotoView view = photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());

        photoService.delete(accountId, view.id());

        assertThat(photoMapper.selectCount(Wrappers.lambdaQuery())).isZero();
        verify(storageService).delete(anyString());
    }

    @Test
    void deleteKeepsObjectWhenSnapshotted() throws Exception {
        ProfilePhotoView view = photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());
        ProfilePhotoEntity photo = photoMapper.selectById(view.id());
        insertRevisionSnapshot(photo.getObjectKey());

        photoService.delete(accountId, view.id());

        verify(storageService, never()).delete(anyString());
        assertThat(photoMapper.selectCount(Wrappers.lambdaQuery())).isZero();
    }

    @Test
    void deleteRejectsForeignPhotoAsNotFound() throws Exception {
        long otherAccountId = insertAccount("photo-foreign");
        GuestProfileEntity otherProfile = new GuestProfileEntity();
        otherProfile.setProfileNo(UUID.randomUUID());
        otherProfile.setUserAccountId(otherAccountId);
        otherProfile.setStatus(ProfileStatus.DRAFT);
        otherProfile.setVersion(0L);
        otherProfile.setCreatedAt(OffsetDateTime.now());
        otherProfile.setUpdatedAt(OffsetDateTime.now());
        profileMapper.insert(otherProfile);
        ProfilePhotoView foreign = photoService.upload(
                otherAccountId, PhotoCategory.AVATAR, imageBytes());

        assertCode(() -> photoService.delete(accountId, foreign.id()), "PHOTO_NOT_FOUND");
    }

    @Test
    void listReturnsOwnedPhotosWithSignedUrls() throws Exception {
        photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());
        photoService.upload(accountId, PhotoCategory.LIFE, imageBytes());

        assertThat(photoService.list(accountId)).hasSize(2);
        assertThat(photoService.list(accountId))
                .allMatch(photo -> photo.downloadUrl().startsWith("https://loveplatform"));
    }

    private long insertAccount(String seed) {
        UserAccountEntity account = new UserAccountEntity();
        account.setPhoneCiphertext(seed.getBytes(StandardCharsets.UTF_8));
        account.setPhoneHmac(seed);
        account.setPasswordHash("not-used");
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedByAdminId(adminMapper.selectList(Wrappers.lambdaQuery()).getFirst().getId());
        account.setActivatedAt(OffsetDateTime.now());
        account.setCreatedAt(OffsetDateTime.now());
        account.setUpdatedAt(OffsetDateTime.now());
        account.setVersion(0L);
        accountMapper.insert(account);
        return account.getId();
    }

    private void insertRevisionSnapshot(String objectKey) {
        OffsetDateTime now = OffsetDateTime.now();
        ProfileRevisionEntity revision = new ProfileRevisionEntity();
        revision.setGuestProfileId(profileId);
        revision.setRevisionNumber(1);
        revision.setStatus(RevisionStatus.APPROVED);
        revision.setSubmittedByAccountId(accountId);
        revision.setSubmittedAt(now);
        revision.setReviewDeadlineAt(now.plusHours(24));
        revision.setSubmissionKeyHmac(UUID.randomUUID().toString());
        revision.setRequestPayloadSha256("b".repeat(64));
        revision.setVersion(0L);
        revision.setCreatedAt(now);
        revisionMapper.insert(revision);

        ProfileRevisionPhotoEntity snapshot = new ProfileRevisionPhotoEntity();
        snapshot.setProfileRevisionId(revision.getId());
        snapshot.setCategory(PhotoCategory.AVATAR);
        snapshot.setObjectKey(objectKey);
        snapshot.setSortOrder(0);
        snapshot.setCreatedAt(now);
        revisionPhotoMapper.insert(snapshot);
    }

    private static byte[] imageBytes() throws Exception {
        BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static void deleteProfileWithOwner(long profileId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM guest_profile WHERE id = ?")) {
            statement.setLong(1, profileId);
            statement.executeUpdate();
        }
    }

    private static void assertCode(Operation operation, String expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    @FunctionalInterface
    private interface Operation {
        void run() throws Exception;
    }
}
