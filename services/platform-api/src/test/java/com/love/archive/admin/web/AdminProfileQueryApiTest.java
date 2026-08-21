package com.love.archive.admin.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.testsupport.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 档案没有审核环节，保存即对管理员可见——包括还没填完的。
 */
class AdminProfileQueryApiTest extends ApiIntegrationTest {

    private static final String TRUSTED_ORIGIN = "https://h5.example.test";
    private static final String PHONE = "13800138000";
    private static final String OTHER_PHONE = "13900139000";
    private static final String PASSWORD = "Guest-profile-query-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private GuestProfileMapper profileMapper;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private StringRedisTemplate redis;
    @MockitoBean private ObjectStorageService storageService;

    private Cookie adminCookie;

    @BeforeEach
    void prepare() throws Exception {
        resetDatabase();
        resetRateLimits(redis);
        insertAdmin();
        adminCookie = adminLogin();
        when(storageService.signDownloadUrl(anyString(), any()))
                .thenReturn("https://bucket.cos.example/signed");
    }

    @Test
    void requiresAdminLogin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/profiles"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/profiles/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsEveryProfileIncludingUnfinishedDrafts() throws Exception {
        saveDraft(registerGuest(mockMvc, PHONE, PASSWORD), true);
        saveDraft(registerGuest(mockMvc, OTHER_PHONE, PASSWORD), false);

        mockMvc.perform(get("/api/v1/admin/profiles").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        // 未填完的档案照样可见，只是状态是 DRAFT。
        mockMvc.perform(get("/api/v1/admin/profiles")
                        .cookie(adminCookie)
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].membershipTier").value("FREE"));
    }

    @Test
    void filtersByPhoneFragment() throws Exception {
        saveDraft(registerGuest(mockMvc, PHONE, PASSWORD), true);
        saveDraft(registerGuest(mockMvc, OTHER_PHONE, PASSWORD), true);

        mockMvc.perform(get("/api/v1/admin/profiles")
                        .cookie(adminCookie)
                        .param("phone", "139"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].phone").value(OTHER_PHONE));
    }

    @Test
    void detailReturnsPlaintextIdentifiersAndSignedPhotoUrls() throws Exception {
        String guestToken = registerGuest(mockMvc, PHONE, PASSWORD);
        saveDraft(guestToken, true);
        long profileId = profileMapper.selectOne(Wrappers.<GuestProfileEntity>lambdaQuery()).getId();

        mockMvc.perform(get("/api/v1/admin/profiles/" + profileId).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value(PHONE))
                .andExpect(jsonPath("$.data.gender").value("男"))
                .andExpect(jsonPath("$.data.city").value("上海"))
                .andExpect(jsonPath("$.data.wechatId").value("wx-real-id"))
                .andExpect(jsonPath("$.data.membershipTier").value("FREE"));
    }

    @Test
    void paginates() throws Exception {
        saveDraft(registerGuest(mockMvc, PHONE, PASSWORD), true);
        saveDraft(registerGuest(mockMvc, OTHER_PHONE, PASSWORD), true);
        saveDraft(registerGuest(mockMvc, "13700137000", PASSWORD), true);

        mockMvc.perform(get("/api/v1/admin/profiles")
                        .cookie(adminCookie)
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void detailRejectsUnknownProfile() throws Exception {
        mockMvc.perform(get("/api/v1/admin/profiles/999999").cookie(adminCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
    }

    private void saveDraft(String guestToken, boolean withCoreFields) throws Exception {
        String body = withCoreFields
                ? """
                  {"expectedVersion":null,"gender":"男","city":"上海","wechatId":"wx-real-id"}
                  """
                : """
                  {"expectedVersion":null,"city":"北京"}
                  """;
        mockMvc.perform(put("/api/v1/guest/profile/draft")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void insertAdmin() {
        char[] password = "profile-query-admin-2026".toCharArray();
        String hash;
        try {
            hash = passwordHasher.hash(password);
        } finally {
            Arrays.fill(password, '\0');
        }
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("profile-query-admin");
        admin.setDisplayName("Profile Query Admin");
        admin.setPasswordHash(hash);
        admin.setStatus(AdminStatus.ACTIVE);
        OffsetDateTime now = OffsetDateTime.now();
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminMapper.insert(admin);
    }

    private Cookie adminLogin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .header("Origin", TRUSTED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"profile-query-admin","password":"profile-query-admin-2026"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("archive-token-admin");
    }

    @SuppressWarnings("unused")
    private static String json(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString()).toString();
    }
}
