package com.love.archive.consent.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.common.security.SensitiveValueProtector;
import com.love.archive.consent.persistence.AuthorizationRecordEntity;
import com.love.archive.consent.persistence.AuthorizationRecordMapper;
import com.love.archive.identity.application.GuestProvisioningService;
import com.love.archive.identity.web.ProvisionedGuestView;
import com.love.archive.testsupport.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class ConsentApiTest extends ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserMapper adminUserMapper;
    @Autowired private GuestProvisioningService provisioningService;
    @Autowired private AuthorizationRecordMapper recordMapper;
    @Autowired private SensitiveValueProtector sensitiveValueProtector;

    private ProvisionedGuestView provisioned;
    private long adminId;

    @BeforeEach
    void preparePaidGuest() {
        resetDatabase();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("consent-api-admin");
        admin.setDisplayName("Consent API Admin");
        admin.setPasswordHash("$argon2id$test-placeholder");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(OffsetDateTime.now());
        admin.setUpdatedAt(OffsetDateTime.now());
        adminUserMapper.insert(admin);
        adminId = admin.getId();
        provisioned = provisioningService.provision(
                adminId,
                "13800138000",
                "PAY-CONSENT-API",
                199_00L,
                OffsetDateTime.now().minusMinutes(5),
                "v0.3",
                null,
                "consent-api-test");
    }

    @Test
    void requiresGuestLoginForConsentEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/guest/consents/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_LOGGED_IN"));

        mockMvc.perform(post("/api/v1/guest/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_LOGGED_IN"));
    }

    @Test
    void returnsExplicitInvalidConsentViewWhenNoValidConsentExists() throws Exception {
        Cookie guestCookie = activateAndLogin();

        mockMvc.perform(get("/api/v1/guest/consents/current").cookie(guestCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasValidConsent").value(false))
                .andExpect(jsonPath("$.data.consent").doesNotExist());
    }

    @Test
    void ignoresForwardedForWithoutTrustedProxyConfiguration() throws Exception {
        Cookie guestCookie = activateAndLogin();

        mockMvc.perform(post("/api/v1/guest/consents")
                        .cookie(guestCookie)
                        .header("Origin", "https://h5.example.test")
                        .header("X-Forwarded-For", "203.0.113.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"authorizationDocumentVersion":"v0.3","accepted":true,
                                "sourcePage":"guest-activation","userAccountId":987654321,
                                "clientIp":"203.0.113.8","userAgent":"override","sessionReference":"raw-token"}
                                """)
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.23");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizationDocumentVersion").value("v0.3"));

        AuthorizationRecordEntity stored = recordMapper.selectOne(Wrappers.<AuthorizationRecordEntity>lambdaQuery()
                .eq(AuthorizationRecordEntity::getUserAccountId, provisioned.accountId()));
        assertThat(stored.getClientIpHmac())
                .isEqualTo(sensitiveValueProtector.hmac("consent:ip", "198.51.100.23"));
        assertThat(stored.getClientIpHmac()).doesNotContain("203.0.113.7");
    }

    private Cookie activateAndLogin() throws Exception {
        mockMvc.perform(post("/api/v1/guest/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13800138000","initialCredential":"%s","newPassword":"New-password-2026"}
                                """.formatted(provisioned.initialCredential())))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/guest/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13800138000","password":"New-password-2026"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getCookies()[0];
    }

    private String validRequest() {
        return """
                {"authorizationDocumentVersion":"v0.3","accepted":true,"sourcePage":"guest-activation"}
                """;
    }
}
