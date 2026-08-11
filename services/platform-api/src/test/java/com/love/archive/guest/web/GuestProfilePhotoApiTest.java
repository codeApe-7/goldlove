package com.love.archive.guest.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.guest.persistence.ProfilePhotoMapper;
import com.love.archive.identity.application.GuestProvisioningService;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.identity.web.ProvisionedGuestView;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.storage.application.StoredObjectView;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class GuestProfilePhotoApiTest extends ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private GuestProvisioningService provisioningService;
    @Autowired private ProfilePhotoMapper photoMapper;
    @Autowired private GuestProfileMapper profileMapper;
    @Autowired private StringRedisTemplate redis;
    @MockitoBean private ObjectStorageService storageService;

    private long adminId;
    private long guestAccountId;
    private String guestToken;

    @BeforeEach
    void prepareAuthenticatedGuest() throws Exception {
        resetDatabase();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        adminId = insertAdmin("photo-api-admin", "photo-admin-2026");
        ProvisionedGuestView guest = provisioningService.provision(
                adminId, "13800138000", "PAY-PHOTO-API", 199_00L,
                OffsetDateTime.now().minusMinutes(5), "v0.3", null, "photo-api-provision");
        guestAccountId = guest.accountId();
        guestToken = activateAndLogin(
                "13800138000", guest.initialCredential(), "Photo-password-2026");
        when(storageService.put(anyString(), any(byte[].class), anyString()))
                .thenAnswer(invocation -> new StoredObjectView(
                        invocation.getArgument(0), "loveplatform-1314980040",
                        ((byte[]) invocation.getArgument(1)).length,
                        invocation.getArgument(2), "c".repeat(64)));
        when(storageService.signDownloadUrl(anyString(), any()))
                .thenReturn("https://loveplatform-1314980040.cos.ap-guangzhou.myqcloud.com/signed");
    }

    @Test
    void photoEndpointsRequireGuestLogin() throws Exception {
        mockMvc.perform(get("/api/v1/guest/profile/photos"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/v1/guest/profile/photo-uploads")
                        .file("file", pngBytes())
                        .param("category", "AVATAR"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadsValidPngReturnsObjectKeyWithoutDatabaseWrites() throws Exception {
        mockMvc.perform(multipart("/api/v1/guest/profile/photo-uploads")
                        .file("file", pngBytes())
                        .param("category", "AVATAR")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.objectKey")
                        .value(org.hamcrest.Matchers.matchesPattern(
                                "profiles/\\d+/avatar/[0-9a-fA-F-]{36}\\.png")))
                .andExpect(jsonPath("$.data.category").value("AVATAR"))
                .andExpect(jsonPath("$.data.previewUrl").isNotEmpty());

        assertThat(photoMapper.selectCount(Wrappers.lambdaQuery())).isZero();
        assertThat(profileMapper.selectCount(Wrappers.lambdaQuery())).isZero();
    }

    @Test
    void rejectsFakeFormatOversizedFileAndInvalidCategory() throws Exception {
        mockMvc.perform(multipart("/api/v1/guest/profile/photo-uploads")
                        .file("file", "not-an-image".getBytes())
                        .param("category", "AVATAR")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_FORMAT_UNSUPPORTED"));

        mockMvc.perform(multipart("/api/v1/guest/profile/photo-uploads")
                        .file("file", new byte[10 * 1024 * 1024 + 1])
                        .param("category", "AVATAR")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"));

        mockMvc.perform(multipart("/api/v1/guest/profile/photo-uploads")
                        .file("file", pngBytes())
                        .param("category", "VIDEO")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_CATEGORY_INVALID"));
    }

    @Test
    void oldPersistentEndpointsAreRemoved() throws Exception {
        mockMvc.perform(multipart("/api/v1/guest/profile/photos")
                        .file("file", pngBytes())
                        .param("category", "AVATAR")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/v1/guest/profile/photos/1")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/guest/profile/photos")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void savesDraftWithPhotoCollectionAndListsPersistedRows() throws Exception {
        String avatarKey = "profiles/" + guestAccountId + "/avatar/"
                + java.util.UUID.randomUUID() + ".jpg";
        mockMvc.perform(put("/api/v1/guest/profile/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + guestToken)
                        .content("""
                                {"expectedVersion":null,
                                 "photos":{"avatar":"%s","life":[]}}
                                """.formatted(avatarKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(get("/api/v1/guest/profile/photos")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].objectKey").value(avatarKey))
                .andExpect(jsonPath("$.data[0].previewUrl").isNotEmpty());
    }

    @Test
    void rejectsCrossAccountPhotoReferenceOnSave() throws Exception {
        mockMvc.perform(put("/api/v1/guest/profile/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + guestToken)
                        .content("""
                                {"expectedVersion":null,
                                 "photos":{"avatar":"profiles/999/avatar/%s.jpg","life":[]}}
                                """.formatted(java.util.UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_REFERENCE_INVALID"));
    }

    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private long insertAdmin(String username, String rawPassword) {
        char[] password = rawPassword.toCharArray();
        String hash;
        try {
            hash = passwordHasher.hash(password);
        } finally {
            Arrays.fill(password, '\0');
        }
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername(username);
        admin.setDisplayName("Photo API Admin");
        admin.setPasswordHash(hash);
        admin.setStatus(AdminStatus.ACTIVE);
        OffsetDateTime now = OffsetDateTime.now();
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminMapper.insert(admin);
        return admin.getId();
    }

    private String activateAndLogin(String phone, String credential, String password)
            throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/guest/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","initialCredential":"%s","newPassword":"%s"}
                                """.formatted(phone, credential, password)))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/guest/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andReturn();
        return new ObjectMapper()
                .readTree(login.getResponse().getContentAsString())
                .get("data")
                .get("accessToken")
                .asText();
    }
}
