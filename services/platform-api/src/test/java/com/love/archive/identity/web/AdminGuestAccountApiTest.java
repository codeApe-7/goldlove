package com.love.archive.identity.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.testsupport.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AdminGuestAccountApiTest extends ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserMapper adminUserMapper;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private StringRedisTemplate redis;

    @BeforeEach
    void cleanState() {
        resetDatabase();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        insertAdmin();
    }

    @Test
    void requiresAdministratorLogin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("PAY-API-001")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_LOGGED_IN"));
    }

    @Test
    void createsPaidGuestAndReturnsInitialCredentialExactlyOnce() throws Exception {
        Cookie adminCookie = login();

        mockMvc.perform(post("/api/v1/admin/accounts")
                        .cookie(adminCookie)
                        .header("Origin", "https://h5.example.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("PAY-API-002")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accountId").isNumber())
                .andExpect(jsonPath("$.data.status").value("PAID_PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.data.initialCredential").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
    }

    @Test
    void validatesPaymentAndMapsDuplicatePhoneToConflict() throws Exception {
        Cookie adminCookie = login();

        mockMvc.perform(post("/api/v1/admin/accounts")
                        .cookie(adminCookie)
                        .header("Origin", "https://h5.example.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("PAY-API-003")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/accounts")
                        .cookie(adminCookie)
                        .header("Origin", "https://h5.example.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("PAY-API-004")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/v1/admin/accounts")
                        .cookie(adminCookie)
                        .header("Origin", "https://h5.example.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900139000","paymentReference":"PAY-API-005",\
                                "amountMinor":0,"paidAt":"2026-08-06T10:00:00+08:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsCookieAuthenticatedMutationWithoutTrustedOrigin() throws Exception {
        Cookie adminCookie = login();

        mockMvc.perform(post("/api/v1/admin/accounts")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("PAY-API-CSRF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_ORIGIN_REJECTED"));
    }

    @Test
    void rejectsAndRevokesAnExistingSessionAfterAdministratorIsDisabled() throws Exception {
        Cookie adminCookie = login();
        adminUserMapper.update(Wrappers.<AdminUserEntity>lambdaUpdate()
                .eq(AdminUserEntity::getUsername, "operator")
                .set(AdminUserEntity::getStatus, AdminStatus.DISABLED));

        mockMvc.perform(post("/api/v1/admin/accounts")
                        .cookie(adminCookie)
                        .header("Origin", "https://h5.example.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("PAY-DISABLED-ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_DISABLED"));

        mockMvc.perform(post("/api/v1/admin/accounts")
                        .cookie(adminCookie)
                        .header("Origin", "https://h5.example.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("PAY-REVOKED-ADMIN")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_LOGGED_IN"));
    }

    @Test
    void reissuesALostActivationCredentialForAPendingAccount() throws Exception {
        Cookie adminCookie = login();
        mockMvc.perform(post("/api/v1/admin/accounts")
                        .cookie(adminCookie)
                        .header("Origin", "https://h5.example.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("PAY-REISSUE-API")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/accounts/activation-credentials/reissue")
                        .cookie(adminCookie)
                        .header("Origin", "https://h5.example.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13800138000"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").isNumber())
                .andExpect(jsonPath("$.data.initialCredential").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("PAID_PENDING_ACTIVATION"));
    }

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"operator","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookies()[0];
    }

    private void insertAdmin() {
        char[] rawPassword = "correct-password".toCharArray();
        String hash;
        try {
            hash = passwordHasher.hash(rawPassword);
        } finally {
            Arrays.fill(rawPassword, '\0');
        }
        OffsetDateTime now = OffsetDateTime.now();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("operator");
        admin.setDisplayName("Operator");
        admin.setPasswordHash(hash);
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminUserMapper.insert(admin);
    }

    private String validRequest(String paymentReference) {
        return """
                {"phone":"13800138000","paymentReference":"%s",\
                "amountMinor":19900,"paidAt":"2026-08-06T10:00:00+08:00","note":"线下付款"}
                """.formatted(paymentReference);
    }
}
