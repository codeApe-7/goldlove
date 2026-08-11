package com.love.archive.review.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.consent.application.ConsentEvidenceCommand;
import com.love.archive.consent.application.ConsentService;
import com.love.archive.guest.domain.PhotoCategory;
import com.love.archive.guest.application.GuestProfileDraftService;
import com.love.archive.guest.application.SaveGuestProfileCommand;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.guest.persistence.ProfilePhotoEntity;
import com.love.archive.guest.persistence.ProfilePhotoMapper;
import com.love.archive.identity.application.GuestProvisioningService;
import com.love.archive.identity.web.ProvisionedGuestView;
import com.love.archive.review.persistence.ProfileRevisionEntity;
import com.love.archive.review.persistence.ProfileRevisionMapper;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class GuestProfileSubmissionApiTest extends ApiIntegrationTest {

    private static final String FIXTURE_AVATAR_UUID = "00000000-0000-0000-0000-000000000002";

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private GuestProvisioningService provisioningService;
    @Autowired private GuestProfileDraftService draftService;
    @Autowired private ConsentService consentService;
    @Autowired private ProfileRevisionMapper revisionMapper;
    @Autowired private GuestProfileMapper profileMapper;
    @Autowired private ProfilePhotoMapper photoMapper;
    @Autowired private StringRedisTemplate redis;
    @MockitoBean private ObjectStorageService storageService;

    private long adminId;
    private ProvisionedGuestView guest;
    private String guestToken;

    @BeforeEach
    void prepareAuthenticatedGuest() throws Exception {
        resetDatabase();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        adminId = insertAdmin();
        guest = provision("13800138000", "PAY-TASK5-API-PRIMARY");
        guestToken = activateAndLogin(
                "13800138000", guest.initialCredential(), "Task5-password-2026");
        when(storageService.signDownloadUrl(anyString(), any()))
                .thenReturn("https://loveplatform-1314980040.cos.ap-guangzhou.myqcloud.com/signed");
    }

    @Test
    void guestSubmissionEndpointsRequireGuestLogin() throws Exception {
        mockMvc.perform(post("/api/v1/guest/profile/submissions")
                        .header("Origin", "https://h5.example.test")
                        .header("Idempotency-Key", "submit-api-anonymous"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_LOGGED_IN"));

        mockMvc.perform(get("/api/v1/guest/profile/revisions/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_LOGGED_IN"));
    }

    @Test
    void requiresIdempotencyKeyHeader() throws Exception {
        mockMvc.perform(post("/api/v1/guest/profile/submissions")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("Origin", "https://h5.example.test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void submitsAsAuthenticatedGuestAndReturnsOwnedRevision() throws Exception {
        saveCompleteDraftAndConsent(guest.accountId());
        long profileId = profileMapper.selectOne(
                        Wrappers.<GuestProfileEntity>lambdaQuery()
                                .eq(GuestProfileEntity::getUserAccountId, guest.accountId()))
                .getId();

        mockMvc.perform(post("/api/v1/guest/profile/submissions")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("Origin", "https://h5.example.test")
                        .header("Idempotency-Key", "submit-api-owned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.revisionNumber").value(1));

        ProfileRevisionEntity revision = revisionMapper.selectOne(
                Wrappers.<ProfileRevisionEntity>lambdaQuery()
                        .eq(ProfileRevisionEntity::getSubmittedByAccountId, guest.accountId()));
        assertThat(revision).isNotNull();
        assertThat(revision.getSubmittedByAccountId()).isEqualTo(guest.accountId());

        mockMvc.perform(get("/api/v1/guest/profile/revisions/{revisionId}", revision.getId())
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(revision.getId()))
                .andExpect(jsonPath("$.data.revisionNumber").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.photos[0].objectKey")
                        .value("profiles/" + profileId + "/avatar/"
                                + FIXTURE_AVATAR_UUID + ".jpg"))
                .andExpect(jsonPath("$.data.photos[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data.photos[0].downloadUrl").isNotEmpty());
    }

    @Test
    void returnsSameNotFoundForForeignAndAbsentRevision() throws Exception {
        saveCompleteDraftAndConsent(guest.accountId());
        mockMvc.perform(post("/api/v1/guest/profile/submissions")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("Origin", "https://h5.example.test")
                        .header("Idempotency-Key", "submit-api-ownership"))
                .andExpect(status().isOk());
        ProfileRevisionEntity revision = revisionMapper.selectOne(
                Wrappers.<ProfileRevisionEntity>lambdaQuery()
                        .eq(ProfileRevisionEntity::getSubmittedByAccountId, guest.accountId()));

        ProvisionedGuestView other = provision("13900139000", "PAY-TASK5-API-OTHER");
        String otherToken = activateAndLogin(
                "13900139000", other.initialCredential(), "Other-password-2026");

        mockMvc.perform(get("/api/v1/guest/profile/revisions/{revisionId}", revision.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_REVISION_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/guest/profile/revisions/{revisionId}", revision.getId() + 999)
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_REVISION_NOT_FOUND"));
    }

    private void saveCompleteDraftAndConsent(long accountId) {
        draftService.save(accountId, new SaveGuestProfileCommand(
                null,
                "男",
                LocalDate.of(1995, 5, 20),
                178,
                "本科",
                "工程师",
                "20-30万",
                "杭州",
                "wx-task5-api-private",
                "dy-task5-api-private",
                "task5 api private nickname",
                URI.create("https://www.douyin.com/user/task5-api-private"),
                List.of()),
                "req-task5-api-draft");
        GuestProfileEntity profile = profileMapper.selectOne(
                Wrappers.<GuestProfileEntity>lambdaQuery()
                        .eq(GuestProfileEntity::getUserAccountId, accountId));
        insertAvatarPhoto(profile.getId());
        consentService.accept(accountId, new ConsentEvidenceCommand(
                "v0.3",
                true,
                "guest-profile",
                "198.51.100.23",
                "Task5 API browser",
                "task5-api-session"));
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

    private ProvisionedGuestView provision(String phone, String paymentReference) {
        return provisioningService.provision(
                adminId,
                phone,
                paymentReference,
                199_00L,
                OffsetDateTime.now().minusMinutes(5),
                "v0.3",
                null,
                "task5-api-provision");
    }

    private String activateAndLogin(String phone, String credential, String password)
            throws Exception {
        mockMvc.perform(post("/api/v1/guest/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","initialCredential":"%s","newPassword":"%s"}
                                """.formatted(phone, credential, password)))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/guest/auth/login")
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

    private long insertAdmin() {
        OffsetDateTime now = OffsetDateTime.now();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("task5-api-admin");
        admin.setDisplayName("Task 5 API Admin");
        admin.setPasswordHash("not-used-in-api-test");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminMapper.insert(admin);
        return admin.getId();
    }
}
