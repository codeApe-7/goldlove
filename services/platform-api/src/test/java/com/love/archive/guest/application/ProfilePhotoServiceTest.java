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
import com.love.archive.guest.persistence.ProfilePhotoMapper;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.storage.application.StoredObjectView;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
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
    @MockitoBean private ObjectStorageService storageService;

    private long accountId;

    @BeforeEach
    void seedActiveAccount() {
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
    void uploadReturnsObjectKeyAndNeverWritesDatabase() throws Exception {
        StagedPhotoView staged = photoService.upload(
                accountId, PhotoCategory.AVATAR, imageBytes());

        assertThat(staged.objectKey()).startsWith("profiles/" + accountId + "/avatar/");
        assertThat(staged.category()).isEqualTo("AVATAR");
        assertThat(staged.previewUrl()).startsWith("https://loveplatform-1314980040");
        assertThat(photoMapper.selectCount(Wrappers.lambdaQuery())).isZero();
        assertThat(profileMapper.selectCount(Wrappers.lambdaQuery())).isZero();
        verify(storageService, never()).delete(anyString());
    }

    @Test
    void uploadRejectsEmptyOversizedAndFakeContent() throws Exception {
        assertCode(() -> photoService.upload(
                        accountId, PhotoCategory.AVATAR, new byte[0]),
                "PHOTO_CONTENT_INVALID");
        assertCode(() -> photoService.upload(
                        accountId, PhotoCategory.AVATAR, new byte[10 * 1024 * 1024 + 1]),
                "PHOTO_TOO_LARGE");
        assertCode(() -> photoService.upload(
                        accountId, PhotoCategory.AVATAR, "not-an-image".getBytes()),
                "PHOTO_FORMAT_UNSUPPORTED");
    }

    @Test
    void listReturnsOnlyPersistedRows() throws Exception {
        assertThat(photoService.list(accountId)).isEmpty();

        photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());

        assertThat(photoService.list(accountId)).isEmpty();
    }

    private long insertAccount(String seed) {
        UserAccountEntity account = new UserAccountEntity();
        account.setPhone("13800138010");
        account.setPasswordHash("not-used");
        account.setStatus(AccountStatus.ACTIVE);
        account.setMembershipTier(com.love.archive.identity.domain.MembershipTier.FREE);
        account.setMembershipCreditMinor(0L);
        account.setCreatedAt(OffsetDateTime.now());
        account.setUpdatedAt(OffsetDateTime.now());
        account.setVersion(0L);
        accountMapper.insert(account);
        return account.getId();
    }

    private static byte[] imageBytes() throws Exception {
        BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
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
