package com.love.archive.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.testsupport.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 管理员停用 / 启用访客账号。停用是账号级别的：状态一改，该手机号下一次调接口就被挡住。
 */
class AdminAccountStatusApiTest extends ApiIntegrationTest {

    private static final String TRUSTED_ORIGIN = "https://h5.example.test";
    private static final String PHONE = "13800138000";
    private static final String PASSWORD = "Guest-account-status-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private UserAccountMapper accountMapper;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private StringRedisTemplate redis;

    private Cookie adminCookie;

    @BeforeEach
    void prepare() throws Exception {
        resetDatabase();
        resetRateLimits(redis);
        insertAdmin();
        adminCookie = adminLogin();
    }

    @Test
    void requiresAdminLogin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts/1/suspend")
                        .header("Origin", TRUSTED_ORIGIN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suspendingBlocksTheGuestOnTheNextRequest() throws Exception {
        String guestToken = registerGuest(mockMvc, PHONE, PASSWORD);
        long accountId = accountIdOf(PHONE);

        // 停用之前访客是正常的。
        mockMvc.perform(get("/api/v1/guest/profile/draft")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk());

        suspend(accountId, "资料不实");

        mockMvc.perform(get("/api/v1/guest/profile/draft")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_INACTIVE"));

        assertThat(statusOf(accountId)).isEqualTo("SUSPENDED");
        assertThat(auditMetadata("ACCOUNT_SUSPENDED")).contains("资料不实");
    }

    @Test
    void activatingLetsTheGuestLogInAgain() throws Exception {
        String guestToken = registerGuest(mockMvc, PHONE, PASSWORD);
        long accountId = accountIdOf(PHONE);
        suspend(accountId, null);

        // 被拦一次的同时 GuestStatusInterceptor 会把会话踢掉，所以旧令牌不会因为启用而复活。
        mockMvc.perform(get("/api/v1/guest/profile/draft")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden());

        activate(accountId);
        assertThat(statusOf(accountId)).isEqualTo("ACTIVE");

        mockMvc.perform(get("/api/v1/guest/profile/draft")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isUnauthorized());

        // 重新登录即可恢复。
        mockMvc.perform(get("/api/v1/guest/profile/draft")
                        .header("Authorization", "Bearer " + guestLogin()))
                .andExpect(status().isOk());
    }

    @Test
    void repeatingTheSameStatusIsIdempotent() throws Exception {
        registerGuest(mockMvc, PHONE, PASSWORD);
        long accountId = accountIdOf(PHONE);

        suspend(accountId, "第一次");
        suspend(accountId, "第二次");

        // 第二次是空操作：状态没变，也不该多写一条审计。
        assertThat(auditCount("ACCOUNT_SUSPENDED")).isEqualTo(1);
        assertThat(auditMetadata("ACCOUNT_SUSPENDED")).contains("第一次").doesNotContain("第二次");
    }

    @Test
    void rejectsUnknownAccountAndOverlongReason() throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts/999999/suspend")
                        .cookie(adminCookie)
                        .header("Origin", TRUSTED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        registerGuest(mockMvc, PHONE, PASSWORD);
        mockMvc.perform(post("/api/v1/admin/accounts/" + accountIdOf(PHONE) + "/suspend")
                        .cookie(adminCookie)
                        .header("Origin", TRUSTED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "长".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsWritesWithoutATrustedOrigin() throws Exception {
        registerGuest(mockMvc, PHONE, PASSWORD);

        mockMvc.perform(post("/api/v1/admin/accounts/" + accountIdOf(PHONE) + "/suspend")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_ORIGIN_REJECTED"));
    }

    private void suspend(long accountId, String reason) throws Exception {
        String body = reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}";
        mockMvc.perform(post("/api/v1/admin/accounts/" + accountId + "/suspend")
                        .cookie(adminCookie)
                        .header("Origin", TRUSTED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }

    private void activate(long accountId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts/" + accountId + "/activate")
                        .cookie(adminCookie)
                        .header("Origin", TRUSTED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    private String guestLogin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/guest/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(PHONE, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }

    private long accountIdOf(String phone) {
        return accountMapper.selectOne(Wrappers.<UserAccountEntity>lambdaQuery()
                        .eq(UserAccountEntity::getPhone, phone))
                .getId();
    }

    private String statusOf(long accountId) {
        return accountMapper.selectById(accountId).getStatus().databaseValue();
    }

    private String auditMetadata(String action) {
        return queryString(
                "SELECT metadata FROM audit_log WHERE action = '" + action + "' ORDER BY id LIMIT 1");
    }

    private long auditCount(String action) {
        return Long.parseLong(queryString(
                "SELECT COUNT(*) FROM audit_log WHERE action = '" + action + "'"));
    }

    private String queryString(String sql) {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : "";
        } catch (SQLException exception) {
            throw new IllegalStateException("读取审计日志失败", exception);
        }
    }

    private void insertAdmin() {
        char[] password = "account-status-admin-2026".toCharArray();
        String hash;
        try {
            hash = passwordHasher.hash(password);
        } finally {
            Arrays.fill(password, '\0');
        }
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("account-status-admin");
        admin.setDisplayName("Account Status Admin");
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
                                {"username":"account-status-admin","password":"account-status-admin-2026"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("archive-token-admin");
    }
}
