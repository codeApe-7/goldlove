package com.love.archive.guest.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.guest.application.GuestProfileDraftService;
import com.love.archive.guest.application.SaveGuestProfileCommand;
import com.love.archive.identity.application.GuestProvisioningService;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.identity.web.ProvisionedGuestView;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.storage.application.StoredObjectView;
import com.love.archive.testsupport.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
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
    @Autowired private GuestProfileDraftService draftService;
    @Autowired private StringRedisTemplate redis;
    @MockitoBean private ObjectStorageService storageService;

    private long adminId;
    private Cookie guestCookie;

    @BeforeEach
    void prepareAuthenticatedGuest() throws Exception {
        resetDatabase();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        adminId = insertAdmin("photo-api-admin", "photo-admin-2026");
        ProvisionedGuestView guest = provisioningService.provision(
                adminId, "13800138000", "PAY-PHOTO-API", 199_00L,
                OffsetDateTime.now().minusMinutes(5), "v0.3", null, "photo-api-provision");
        guestCookie = activateAndLogin(
                "13800138000", guest.initialCredential(), "Photo-password-2026");
        draftService.save(guest.accountId(), new SaveGuestProfileCommand(
                null, "男", LocalDate.of(1995, 5, 20), 178, "本科",
                "工程师", "20-30万", "杭州", "wx-photo-api", "dy-photo-api",
                "photo api nickname",
                URI.create("https://www.douyin.com/user/photo-api"),
                List.of()), "photo-api-draft");
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
        mockMvc.perform(multipart("/api/v1/guest/profile/photos")
                        .file("file", pngBytes())
                        .param("category", "AVATAR"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadsValidPngAndReturns201() throws Exception {
        mockMvc.perform(multipart("/api/v1/guest/profile/photos")
                        .file("file", pngBytes())
                        .param("category", "AVATAR")
                        .header("Origin", "https://h5.example.test")
                        .cookie(guestCookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category").value("AVATAR"))
                .andExpect(jsonPath("$.data.downloadUrl").isNotEmpty());
    }

    @Test
    void rejectsFakeFormatOversizedFileAndInvalidCategory() throws Exception {
        mockMvc.perform(multipart("/api/v1/guest/profile/photos")
                        .file("file", "not-an-image".getBytes())
                        .param("category", "AVATAR")
                        .header("Origin", "https://h5.example.test")
                        .cookie(guestCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_FORMAT_UNSUPPORTED"));

        mockMvc.perform(multipart("/api/v1/guest/profile/photos")
                        .file("file", new byte[10 * 1024 * 1024 + 1])
                        .param("category", "AVATAR")
                        .header("Origin", "https://h5.example.test")
                        .cookie(guestCookie))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"));

        mockMvc.perform(multipart("/api/v1/guest/profile/photos")
                        .file("file", pngBytes())
                        .param("category", "VIDEO")
                        .header("Origin", "https://h5.example.test")
                        .cookie(guestCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_CATEGORY_INVALID"));
    }

    @Test
    void deletesOwnedPhoto() throws Exception {
        MvcResult upload = mockMvc.perform(multipart("/api/v1/guest/profile/photos")
                        .file("file", pngBytes())
                        .param("category", "LIFE")
                        .header("Origin", "https://h5.example.test")
                        .cookie(guestCookie))
                .andExpect(status().isCreated())
                .andReturn();
        long photoId = new ObjectMapper()
                .readTree(upload.getResponse().getContentAsString())
                .get("data")
                .get("id")
                .asLong();

        mockMvc.perform(delete("/api/v1/guest/profile/photos/{photoId}", photoId)
                        .header("Origin", "https://h5.example.test")
                        .cookie(guestCookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/guest/profile/photos").cookie(guestCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
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

    private Cookie activateAndLogin(String phone, String credential, String password)
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
        return login.getResponse().getCookies()[0];
    }
}
