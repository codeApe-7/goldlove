package com.love.archive.identity.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.identity.application.GuestProvisioningService;
import com.love.archive.testsupport.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class GuestAuthApiTest extends ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private GuestProvisioningService provisioningService;
    @Autowired private AdminUserMapper adminUserMapper;
    @Autowired private StringRedisTemplate redis;

    private Long adminId;

    @BeforeEach
    void cleanState() {
        resetDatabase();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        OffsetDateTime now = OffsetDateTime.now();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("guest-api-admin");
        admin.setDisplayName("Guest API Admin");
        admin.setPasswordHash("$argon2id$test-placeholder");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
    }

    @Test
    void activatesLogsInReadsSessionAndLogsOut() throws Exception {
        ProvisionedGuestView provisioned = provision("13800138000", "PAY-GUEST-API-001");

        mockMvc.perform(post("/api/v1/guest/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activationBody("13800138000", provisioned.initialCredential())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        MvcResult login = mockMvc.perform(post("/api/v1/guest/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13800138000","password":"New-password-2026"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("archive-token-guest="),
                        org.hamcrest.Matchers.containsString("Secure"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax"))))
                .andReturn();
        Cookie guestCookie = login.getResponse().getCookies()[0];

        mockMvc.perform(get("/api/v1/guest/auth/me").cookie(guestCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(provisioned.accountId()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/guest/auth/logout")
                        .cookie(guestCookie)
                        .header("Origin", "https://h5.example.test"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/guest/auth/me").cookie(guestCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_LOGGED_IN"));
    }

    @Test
    void hidesPendingAccountsAndUsesUniformInvalidCredentialErrors() throws Exception {
        provision("13800138000", "PAY-GUEST-API-002");

        mockMvc.perform(post("/api/v1/guest/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13800138000","password":"any-password-2026"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

        ProvisionedGuestView active = provision("13900139000", "PAY-GUEST-API-003");
        mockMvc.perform(post("/api/v1/guest/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activationBody("13900139000", active.initialCredential())))
                .andExpect(status().isOk());

        assertInvalidLogin("13900139000", "wrong-password");
        assertInvalidLogin("13700137000", "wrong-password");
    }

    private void assertInvalidLogin(String phone, String password) throws Exception {
        mockMvc.perform(post("/api/v1/guest/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("手机号或密码错误"));
    }

    private ProvisionedGuestView provision(String phone, String paymentReference) {
        return provisioningService.provision(
                adminId,
                phone,
                paymentReference,
                199_00L,
                OffsetDateTime.now().minusMinutes(5),
                null,
                "guest-auth-api-test");
    }

    private String activationBody(String phone, String credential) {
        return """
                {"phone":"%s","initialCredential":"%s","newPassword":"New-password-2026"}
                """.formatted(phone, credential);
    }
}
