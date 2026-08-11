package com.love.archive.review.web;

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
import com.love.archive.guest.application.GuestProfileDraftService;
import com.love.archive.guest.application.SaveGuestProfileCommand;
import com.love.archive.guest.domain.PhotoCategory;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.guest.persistence.ProfilePhotoEntity;
import com.love.archive.guest.persistence.ProfilePhotoMapper;
import com.love.archive.identity.application.GuestProvisioningService;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.identity.web.ProvisionedGuestView;
import com.love.archive.review.application.ProfileReviewService;
import com.love.archive.review.application.ProfileSubmissionService;
import com.love.archive.review.persistence.ProfileRevisionEntity;
import com.love.archive.review.persistence.ProfileRevisionMapper;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.testsupport.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AdminProfileReviewApiTest extends ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private GuestProvisioningService provisioningService;
    @Autowired private GuestProfileDraftService draftService;
    @Autowired private GuestProfileMapper guestProfileMapper;
    @Autowired private ProfilePhotoMapper photoMapper;
    @Autowired private ConsentService consentService;
    @Autowired private ProfileSubmissionService submissionService;
    @Autowired private ProfileReviewService reviewService;
    @Autowired private ProfileRevisionMapper revisionMapper;
    @Autowired private StringRedisTemplate redis;
    @MockitoBean private ObjectStorageService storageService;

    private long adminId;
    private Cookie adminCookie;
    private long guestAccountId;
    private long revisionId;

    @BeforeEach
    void prepareAuthenticatedAdminAndPendingReview() throws Exception {
        resetDatabase();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        adminId = insertAdmin("operator", "admin-password-2026");
        adminCookie = login("operator", "admin-password-2026");
        ProvisionedGuestView guest = provision("13800138000", "PAY-TASK6-API-PRIMARY");
        activateGuest("13800138000", guest.initialCredential(), "Task6-password-2026");
        guestAccountId = guest.accountId();
        saveCompleteDraftAndConsent(guestAccountId);
        revisionId = submissionService.submit(
                        guestAccountId, "review-api-submit", "review-api-request")
                .id();
        when(storageService.signDownloadUrl(anyString(), any()))
                .thenReturn("https://loveplatform-1314980040.cos.ap-guangzhou.myqcloud.com/signed");
    }

    @Test
    void adminReviewEndpointsRequireLogin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/profile-reviews"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_LOGGED_IN"));
    }

    @Test
    void requiresActiveAdministratorSession() throws Exception {
        adminMapper.update(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<AdminUserEntity>lambdaUpdate()
                .eq(AdminUserEntity::getId, adminId)
                .set(AdminUserEntity::getStatus, AdminStatus.DISABLED));

        mockMvc.perform(get("/api/v1/admin/profile-reviews").cookie(adminCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_DISABLED"));
    }

    @Test
    void listsPendingReviewsWithTypedPageMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/admin/profile-reviews")
                        .param("page", "1")
                        .param("size", "20")
                        .param("status", "PENDING")
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].revisionId").value(revisionId))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].guestProfileId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].submissionKeyHmac").doesNotExist());
    }

    @Test
    void approvesThenRejectsChangedDraftThroughAdminEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/admin/profile-reviews/{revisionId}/approve", revisionId)
                        .header("Origin", "https://h5.example.test")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedAt").isNotEmpty());

        ProfileRevisionEntity approved = revisionMapper.selectById(revisionId);
        assertThat(approved.getStatus()).isEqualTo(
                com.love.archive.review.domain.RevisionStatus.APPROVED);

        long secondRevisionId = submitChangedDraft();
        mockMvc.perform(post("/api/v1/admin/profile-reviews/{revisionId}/reject", secondRevisionId)
                        .header("Origin", "https://h5.example.test")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,
                                 "reasonCode":"CONTENT_INCOMPLETE",
                                 "comment":"请补充职业信息"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(get("/api/v1/admin/profile-reviews/{revisionId}", secondRevisionId)
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastApprovedRevisionId").value(revisionId))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void rejectRequiresGuestFacingComment() throws Exception {
        mockMvc.perform(post("/api/v1/admin/profile-reviews/{revisionId}/reject", revisionId)
                        .header("Origin", "https://h5.example.test")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"comment\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FIELD_VALUE_INVALID"));
    }

    private long submitChangedDraft() throws Exception {
        GuestProfileEntity profile = guestProfileMapper.selectOne(
                Wrappers.<GuestProfileEntity>lambdaQuery()
                        .eq(GuestProfileEntity::getUserAccountId, guestAccountId));
        draftService.save(guestAccountId, new SaveGuestProfileCommand(
                profile.getVersion(),
                "男",
                LocalDate.of(1995, 5, 20),
                178,
                "本科",
                "工程师",
                "20-30万",
                "宁波",
                "wx-task6-api",
                "dy-task6-api",
                "task6 api nickname",
                URI.create("https://www.douyin.com/user/task6-api"),
                List.of()),
                "review-api-draft-changed");
        return submissionService.submit(
                        guestAccountId, "review-api-submit-changed", "review-api-request-changed")
                .id();
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
                "wx-task6-api",
                "dy-task6-api",
                "task6 api nickname",
                URI.create("https://www.douyin.com/user/task6-api"),
                List.of()),
                "review-api-draft");
        GuestProfileEntity profile = guestProfileMapper.selectOne(
                Wrappers.<GuestProfileEntity>lambdaQuery()
                        .eq(GuestProfileEntity::getUserAccountId, accountId));
        insertAvatarPhoto(profile.getId());
        consentService.accept(accountId, new ConsentEvidenceCommand(
                "v0.3",
                true,
                "guest-activation",
                "203.0.113.8",
                "Mozilla/5.0",
                "task6-api-session"));
    }

    private void insertAvatarPhoto(long profileId) {
        ProfilePhotoEntity photo = new ProfilePhotoEntity();
        photo.setGuestProfileId(profileId);
        photo.setCategory(PhotoCategory.AVATAR);
        photo.setObjectKey("profiles/" + profileId + "/avatar/fixture.jpg");
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
                OffsetDateTime.parse("2030-07-01T10:10:30Z"),
                "v0.3",
                null,
                "review-api-provision");
    }

    private void activateGuest(String phone, String initialCredential, String password)
            throws Exception {
        mockMvc.perform(post("/api/v1/guest/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s",
                                 "initialCredential":"%s",
                                 "newPassword":"%s"}
                                """.formatted(phone, initialCredential, password)))
                .andExpect(status().isOk());
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
        admin.setDisplayName("Review API Admin");
        admin.setPasswordHash(hash);
        admin.setStatus(AdminStatus.ACTIVE);
        OffsetDateTime now = OffsetDateTime.parse("2030-07-01T10:00:00Z");
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminMapper.insert(admin);
        return admin.getId();
    }

    private Cookie login(String username, String rawPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, rawPassword)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("archive-token-admin");
    }
}
