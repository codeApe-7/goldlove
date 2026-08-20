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
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class ActivationCodeApiTest extends ApiIntegrationTest {

    private static final String TRUSTED_ORIGIN = "https://h5.example.test";
    private static final String PHONE = "13800138000";
    private static final String OTHER_PHONE = "13900139000";
    private static final String PASSWORD = "Guest-activation-2026";

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
    void requiresAdminLoginToGenerate() throws Exception {
        mockMvc.perform(post("/api/v1/admin/activation-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(PHONE, "VIP")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresBoundPhoneWhenGenerating() throws Exception {
        mockMvc.perform(post("/api/v1/admin/activation-codes")
                        .cookie(adminCookie)
                        .header("Origin", TRUSTED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"boundPhone":"","grantedTier":"VIP"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/activation-codes")
                        .cookie(adminCookie)
                        .header("Origin", TRUSTED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody("1234", "VIP")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_INVALID"));
    }

    @Test
    void redeemingUpgradesToVipWithoutCountingAsPaidCredit() throws Exception {
        String code = generateCode(PHONE, "VIP");
        String guestToken = registerGuest(mockMvc, PHONE, PASSWORD);

        mockMvc.perform(post("/api/v1/guest/membership/activation-codes")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("VIP"))
                // 兑码不是付费，绝不能顶 SVIP 的累计阈值。
                .andExpect(jsonPath("$.data.creditMinor").value(0));

        mockMvc.perform(get("/api/v1/guest/membership")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("VIP"));
        assertThat(accountMapper.selectOne(Wrappers.<UserAccountEntity>lambdaQuery()
                        .eq(UserAccountEntity::getPhone, PHONE))
                .getMembershipCreditMinor()).isZero();
    }

    @Test
    void acceptsLowercaseAndSpacedInputFromTheUser() throws Exception {
        String code = generateCode(PHONE, "VIP");
        String guestToken = registerGuest(mockMvc, PHONE, PASSWORD);

        mockMvc.perform(post("/api/v1/guest/membership/activation-codes")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(" " + code.toLowerCase(java.util.Locale.ROOT) + " ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("VIP"));
    }

    @Test
    void refusesCodesBoundToAnotherPhone() throws Exception {
        String code = generateCode(OTHER_PHONE, "VIP");
        String guestToken = registerGuest(mockMvc, PHONE, PASSWORD);

        mockMvc.perform(post("/api/v1/guest/membership/activation-codes")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(code)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVATION_CODE_PHONE_MISMATCH"));

        mockMvc.perform(get("/api/v1/guest/membership")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(jsonPath("$.data.tier").value("FREE"));
    }

    @Test
    void refusesUnknownAndAlreadyRedeemedCodes() throws Exception {
        String code = generateCode(PHONE, "VIP");
        String guestToken = registerGuest(mockMvc, PHONE, PASSWORD);

        mockMvc.perform(post("/api/v1/guest/membership/activation-codes")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody("LOVE-ZZZZ-ZZZZ-ZZZZ")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVATION_CODE_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/guest/membership/activation-codes")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(code)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/guest/membership/activation-codes")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(code)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVATION_CODE_USED"));
    }

    @Test
    void revokedCodesCannotBeRedeemed() throws Exception {
        MvcResult generated = generate(PHONE, "VIP");
        long codeId = readLong(generated, "id");
        String code = readText(generated, "code");

        mockMvc.perform(post("/api/v1/admin/activation-codes/" + codeId + "/revoke")
                        .cookie(adminCookie)
                        .header("Origin", TRUSTED_ORIGIN))
                .andExpect(status().isOk());
        // 已作废的码不能再作废第二次。
        mockMvc.perform(post("/api/v1/admin/activation-codes/" + codeId + "/revoke")
                        .cookie(adminCookie)
                        .header("Origin", TRUSTED_ORIGIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVATION_CODE_NOT_REVOCABLE"));

        String guestToken = registerGuest(mockMvc, PHONE, PASSWORD);
        mockMvc.perform(post("/api/v1/guest/membership/activation-codes")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(code)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVATION_CODE_REVOKED"));
    }

    @Test
    void svipCodesGrantSvipDirectly() throws Exception {
        String code = generateCode(PHONE, "SVIP");
        String guestToken = registerGuest(mockMvc, PHONE, PASSWORD);

        mockMvc.perform(post("/api/v1/guest/membership/activation-codes")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("SVIP"));
    }

    @Test
    void adminListShowsWhetherTheBoundPhoneIsAlreadyRegisteredAndWhoRedeemed() throws Exception {
        String code = generateCode(PHONE, "VIP");

        // 还没注册：提醒管理员该号可能被别人抢注。
        mockMvc.perform(get("/api/v1/admin/activation-codes").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value(code))
                .andExpect(jsonPath("$.data.items[0].boundPhoneRegistered").value(false))
                .andExpect(jsonPath("$.data.items[0].status").value("UNUSED"));

        String guestToken = registerGuest(mockMvc, PHONE, PASSWORD);
        mockMvc.perform(post("/api/v1/guest/membership/activation-codes")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody(code)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/activation-codes")
                        .cookie(adminCookie)
                        .param("status", "USED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].boundPhoneRegistered").value(true))
                .andExpect(jsonPath("$.data.items[0].redeemedPhone").value(PHONE))
                .andExpect(jsonPath("$.data.items[0].redeemedAt").isNotEmpty());
    }

    private String generateCode(String phone, String tier) throws Exception {
        return readText(generate(phone, tier), "code");
    }

    private MvcResult generate(String phone, String tier) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/activation-codes")
                        .cookie(adminCookie)
                        .header("Origin", TRUSTED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(phone, tier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").isNotEmpty())
                .andReturn();
    }

    private static String generateBody(String phone, String tier) {
        return """
                {"boundPhone":"%s","grantedTier":"%s","note":"线下收款"}
                """.formatted(phone, tier);
    }

    private static String redeemBody(String code) {
        return "{\"code\":\"" + code + "\"}";
    }

    private static String readText(MvcResult result, String field) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .path("data").path(field).asText();
    }

    private static long readLong(MvcResult result, String field) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .path("data").path(field).asLong();
    }

    private void insertAdmin() {
        char[] password = "activation-admin-2026".toCharArray();
        String hash;
        try {
            hash = passwordHasher.hash(password);
        } finally {
            Arrays.fill(password, '\0');
        }
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("activation-admin");
        admin.setDisplayName("Activation Admin");
        admin.setPasswordHash(hash);
        admin.setStatus(AdminStatus.ACTIVE);
        OffsetDateTime now = OffsetDateTime.now();
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminMapper.insert(admin);
    }

    private Cookie adminLogin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"activation-admin","password":"activation-admin-2026"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("archive-token-admin");
    }
}
