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
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.guest.persistence.ProfilePhotoEntity;
import com.love.archive.guest.persistence.ProfilePhotoMapper;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.storage.application.StoredObjectView;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class GuestProfilePhotoSaveTest extends ApiIntegrationTest {

    private static final String REQUEST_ID = "req-photo-save";

    @Autowired private GuestProfileDraftService draftService;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private UserAccountMapper accountMapper;
    @Autowired private GuestProfileMapper profileMapper;
    @Autowired private ProfilePhotoMapper photoMapper;
    @MockitoBean private ObjectStorageService storageService;

    private long accountId;

    @BeforeEach
    void seedActiveAccount() {
        resetDatabase();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("photo-save-admin");
        admin.setDisplayName("Photo Save Admin");
        admin.setPasswordHash("not-used");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(OffsetDateTime.now());
        admin.setUpdatedAt(OffsetDateTime.now());
        adminMapper.insert(admin);

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
        accountId = account.getId();

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
    void firstSaveCreatesSlimPhotoRows() {
        draftService.save(accountId, command(null, target(avatarKey("a"), List.of(lifeKey("b")))), REQUEST_ID);

        List<ProfilePhotoEntity> rows = photoMapper.selectList(Wrappers.lambdaQuery());
        assertThat(rows).extracting(ProfilePhotoEntity::getObjectKey)
                .containsExactlyInAnyOrder(avatarKey("a"), lifeKey("b"));
        assertThat(rows).extracting(ProfilePhotoEntity::getSortOrder)
                .containsExactlyInAnyOrder(0, 0);
    }

    @Test
    void resaveReplacesRemovesAndReorders() {
        draftService.save(accountId, command(null, target(
                avatarKey("a"), List.of(lifeKey("b"), lifeKey("c")))), REQUEST_ID);

        draftService.save(accountId, command(currentVersion(), target(
                avatarKey("d"), List.of(lifeKey("c"), lifeKey("b")))), REQUEST_ID);

        List<ProfilePhotoEntity> rows = photoMapper.selectList(
                Wrappers.<ProfilePhotoEntity>lambdaQuery()
                        .orderByAsc(ProfilePhotoEntity::getCategory)
                        .orderByAsc(ProfilePhotoEntity::getSortOrder));
        assertThat(rows).extracting(ProfilePhotoEntity::getObjectKey)
                .containsExactly(avatarKey("d"), lifeKey("c"), lifeKey("b"));
        assertThat(rows).extracting(ProfilePhotoEntity::getCategory)
                .containsExactly(PhotoCategory.AVATAR, PhotoCategory.LIFE, PhotoCategory.LIFE);
        assertThat(photoMapper.selectCount(
                        Wrappers.<ProfilePhotoEntity>lambdaQuery()
                                .eq(ProfilePhotoEntity::getObjectKey, avatarKey("a"))))
                .isZero();
    }

    @Test
    void rejectsInvalidDuplicateOrOversizedPhotoCollections() {
        assertCode(() -> draftService.save(accountId, command(null, target(
                        avatarKey("a"), List.of(avatarKey("b")))), REQUEST_ID),
                "PHOTO_REFERENCE_INVALID");
        assertCode(() -> draftService.save(accountId, command(null, target(
                        "profiles/999/avatar/" + uuid() + ".jpg", List.of())), REQUEST_ID),
                "PHOTO_REFERENCE_INVALID");
        assertCode(() -> draftService.save(accountId, command(null, target(
                        null, List.of(avatarKey("a")))), REQUEST_ID),
                "PHOTO_REFERENCE_INVALID");
        assertCode(() -> draftService.save(accountId, command(null, target(
                        null, java.util.stream.IntStream.range(0, 7)
                                .mapToObj(i -> lifeKey("x" + i)).toList())), REQUEST_ID),
                "PHOTO_COUNT_LIMIT_EXCEEDED");
    }

    @Test
    void deletesUnreferencedObjectsOnlyAfterCommit() {
        draftService.save(accountId, command(null, target(avatarKey("a"), List.of())), REQUEST_ID);

        draftService.save(accountId, command(currentVersion(), target(avatarKey("b"), List.of())), REQUEST_ID);

        verify(storageService).delete(avatarKey("a"));
    }

    @Test
    void saveTransactionNeverCallsObjectStoragePut() {
        draftService.save(accountId, command(null, target(avatarKey("a"), List.of())), REQUEST_ID);

        verify(storageService, never()).put(anyString(), any(byte[].class), anyString());
    }

    private String avatarKey(String uuidSuffix) {
        return "profiles/" + accountId + "/avatar/"
                + UUID.nameUUIDFromBytes(("avatar-" + uuidSuffix).getBytes(StandardCharsets.UTF_8))
                + ".jpg";
    }

    private String lifeKey(String uuidSuffix) {
        return "profiles/" + accountId + "/life/"
                + UUID.nameUUIDFromBytes(("life-" + uuidSuffix).getBytes(StandardCharsets.UTF_8))
                + ".jpg";
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static ProfilePhotoTarget target(String avatar, List<String> life) {
        return new ProfilePhotoTarget(avatar, life);
    }

    private Long currentVersion() {
        return profileMapper.selectList(Wrappers.lambdaQuery()).getFirst().getVersion();
    }

    private static SaveGuestProfileCommand command(Long expectedVersion, ProfilePhotoTarget photos) {
        return new SaveGuestProfileCommand(
                expectedVersion, "男", null, null, null, null, null, null,
                null, null, null, null, List.of(), photos);
    }

    private static void assertCode(Operation operation, String expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    @FunctionalInterface
    private interface Operation {
        void run();
    }
}
