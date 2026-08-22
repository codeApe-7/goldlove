package com.love.archive.admin.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.testsupport.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    @Autowired private UserAccountMapper accountMapper;
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
    void filtersByKeywordAcrossPhoneAndProfileNo() throws Exception {
        saveDraft(registerGuest(mockMvc, PHONE, PASSWORD), true);
        saveDraft(registerGuest(mockMvc, OTHER_PHONE, PASSWORD), true);

        mockMvc.perform(get("/api/v1/admin/profiles")
                        .cookie(adminCookie)
                        .param("keyword", "139"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].phone").value(OTHER_PHONE));

        // 同一个输入框还要能命中档案编号——列表上展示的就是它的前 8 位。
        String profileNo = profileMapper
                .selectOne(Wrappers.<GuestProfileEntity>lambdaQuery()
                        .eq(GuestProfileEntity::getUserAccountId, accountIdOf(OTHER_PHONE)))
                .getProfileNo()
                .toString();
        mockMvc.perform(get("/api/v1/admin/profiles")
                        .cookie(adminCookie)
                        .param("keyword", profileNo.substring(0, 8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].phone").value(OTHER_PHONE));
    }

    @Test
    void listExposesTheColumnsTheAdminTableNeeds() throws Exception {
        saveDraft(registerGuest(mockMvc, PHONE, PASSWORD), true);
        String profileNo = profileMapper.selectOne(Wrappers.<GuestProfileEntity>lambdaQuery())
                .getProfileNo()
                .toString();

        mockMvc.perform(get("/api/v1/admin/profiles").cookie(adminCookie))
                .andExpect(status().isOk())
                // 档案编号一定要真的下发。它在库里是 uuid 列，映射到自定义 row 类时
                // MyBatis 解析不出 java.util.UUID 的类型处理器会把它静默置成 null，
                // 前端拿到 null 再去 slice 就整页崩。SQL 里已 ::text，这条断言守住它。
                .andExpect(jsonPath("$.data.items[0].profileNo").value(profileNo))
                .andExpect(jsonPath("$.data.items[0].accountId").isNumber())
                .andExpect(jsonPath("$.data.items[0].accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].gender").value("男"))
                .andExpect(jsonPath("$.data.items[0].city").value("上海"))
                .andExpect(jsonPath("$.data.items[0].createdAt").exists());
    }

    @Test
    void countsCoverEveryTabAndIgnoreTabConditions() throws Exception {
        saveDraft(registerGuest(mockMvc, PHONE, PASSWORD), true);
        saveDraft(registerGuest(mockMvc, OTHER_PHONE, PASSWORD), false);
        suspend(accountIdOf(OTHER_PHONE));

        mockMvc.perform(get("/api/v1/admin/profiles/counts").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.draft").value(2))
                .andExpect(jsonPath("$.data.completed").value(0))
                .andExpect(jsonPath("$.data.suspended").value(1))
                .andExpect(jsonPath("$.data.paid").value(0));

        // 筛选条上的条件要参与统计，tab 自身的条件（status / accountStatus）不参与——
        // 否则切到某个 tab 之后其他 tab 的数字会全变 0。
        mockMvc.perform(get("/api/v1/admin/profiles/counts")
                        .cookie(adminCookie)
                        .param("keyword", "139"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.suspended").value(1));
    }

    @Test
    void filtersBySortAccountStatusAndCityPrefix() throws Exception {
        saveDraft(registerGuest(mockMvc, PHONE, PASSWORD), true);
        saveDraft(registerGuest(mockMvc, OTHER_PHONE, PASSWORD), false);
        suspend(accountIdOf(OTHER_PHONE));

        mockMvc.perform(get("/api/v1/admin/profiles")
                        .cookie(adminCookie)
                        .param("accountStatus", "SUSPENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].phone").value(OTHER_PHONE));

        mockMvc.perform(get("/api/v1/admin/profiles")
                        .cookie(adminCookie)
                        .param("city", "上海"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].phone").value(PHONE));

        mockMvc.perform(get("/api/v1/admin/profiles")
                        .cookie(adminCookie)
                        .param("sort", "PHONE_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].phone").value(PHONE));

        // 排序参数是白名单枚举，注入不了 SQL 片段；错误码要指向 sort 这个参数本身，
        // 而不是沿用照片类别那句「照片类别不正确」。
        mockMvc.perform(get("/api/v1/admin/profiles")
                        .cookie(adminCookie)
                        .param("sort", "p.id; DROP TABLE guest_profile"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_PARAM_INVALID"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("sort")));
    }

    @Test
    void exportsCsvWithBomAndWritesAnAuditRecord() throws Exception {
        saveDraft(registerGuest(mockMvc, PHONE, PASSWORD), true);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/profiles/export").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".csv")))
                .andReturn();

        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // Excel 只有见到 BOM 才把 CSV 当 UTF-8 读。
        assertThat(csv).startsWith("﻿");
        assertThat(csv).contains("档案编号,手机号,账号状态");
        assertThat(csv).contains(PHONE).contains("wx-real-id");
        assertThat(auditActions()).contains("PROFILE_EXPORTED");
    }

    @Test
    void detailReturnsPlaintextIdentifiersAndSignedPhotoUrls() throws Exception {
        String guestToken = registerGuest(mockMvc, PHONE, PASSWORD);
        saveDraft(guestToken, true);
        GuestProfileEntity profile = profileMapper.selectOne(Wrappers.<GuestProfileEntity>lambdaQuery());

        mockMvc.perform(get("/api/v1/admin/profiles/" + profile.getId()).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileNo").value(profile.getProfileNo().toString()))
                .andExpect(jsonPath("$.data.phone").value(PHONE))
                .andExpect(jsonPath("$.data.gender").value("男"))
                .andExpect(jsonPath("$.data.city").value("上海"))
                .andExpect(jsonPath("$.data.wechatId").value("wx-real-id"))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.accountId").isNumber())
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

    private long accountIdOf(String phone) {
        return accountMapper.selectOne(Wrappers.<UserAccountEntity>lambdaQuery()
                        .eq(UserAccountEntity::getPhone, phone))
                .getId();
    }

    /** 停用走真实接口，顺带保证 admin 的 CSRF 来源校验在这条路径上也是通的。 */
    private void suspend(long accountId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts/" + accountId + "/suspend")
                        .cookie(adminCookie)
                        .header("Origin", TRUSTED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"资料不实\"}"))
                .andExpect(status().isOk());
    }

    private List<String> auditActions() {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT action FROM audit_log")) {
            List<String> actions = new ArrayList<>();
            while (rows.next()) {
                actions.add(rows.getString(1));
            }
            return actions;
        } catch (SQLException exception) {
            throw new IllegalStateException("读取审计日志失败", exception);
        }
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
