package com.love.archive.admin.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

class AdminAuthApiTest extends ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void cleanState() {
        jdbcClient.sql("DELETE FROM admin_user").update();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void logsInActiveAdminAndReturnsSecureCookieWithRequestId() throws Exception {
        insertAdmin("operator", "correct-password", AdminStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .header("X-Request-ID", "admin-login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"operator","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "admin-login-request"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("archive-token-admin="),
                        org.hamcrest.Matchers.containsString("Secure"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax"))))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").value("admin-login-request"))
                .andExpect(jsonPath("$.data.username").value("operator"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void wrongUsernameAndWrongPasswordUseTheSamePublicError() throws Exception {
        insertAdmin("operator", "correct-password", AdminStatus.ACTIVE);

        assertInvalidCredentials("missing", "correct-password");
        assertInvalidCredentials("operator", "wrong-password");
    }

    @Test
    void rejectsDisabledAdministrator() throws Exception {
        insertAdmin("disabled", "correct-password", AdminStatus.DISABLED);

        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"disabled","password":"correct-password"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_DISABLED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private void assertInvalidCredentials(String username, String password) throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    private void insertAdmin(String username, String password, AdminStatus status) {
        char[] value = password.toCharArray();
        String passwordHash;
        try {
            passwordHash = passwordHasher.hash(value);
        } finally {
            Arrays.fill(value, '\0');
        }

        OffsetDateTime now = OffsetDateTime.now();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername(username);
        admin.setDisplayName(username);
        admin.setPasswordHash(passwordHash);
        admin.setStatus(status);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminUserMapper.insert(admin);
    }
}
