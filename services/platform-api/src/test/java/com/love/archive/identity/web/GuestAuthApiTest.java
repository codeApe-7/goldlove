package com.love.archive.identity.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.testsupport.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class GuestAuthApiTest extends ApiIntegrationTest {

    private static final String PHONE = "13800138000";
    private static final String PASSWORD = "New-password-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountMapper accountMapper;
    @Autowired private StringRedisTemplate redis;

    @BeforeEach
    void cleanState() {
        resetDatabase();
        resetRateLimits(redis);
    }

    @Test
    void registersLogsInReadsSessionAndLogsOut() throws Exception {
        mockMvc.perform(post("/api/v1/guest/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(PHONE, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.membershipTier").value("FREE"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(2592000));

        MvcResult login = mockMvc.perform(post("/api/v1/guest/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(PHONE, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.membershipTier").value("FREE"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        String token = accessToken(login);

        mockMvc.perform(get("/api/v1/guest/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/guest/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/guest/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_LOGGED_IN"));
    }

    @Test
    void rejectsDuplicatePhone() throws Exception {
        registerGuest(mockMvc, PHONE, PASSWORD);

        mockMvc.perform(post("/api/v1/guest/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(PHONE, "Another-password-2026")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));
    }

    @Test
    void rejectsMalformedPhoneWeakPasswordAndMismatchedConfirmation() throws Exception {
        mockMvc.perform(post("/api/v1/guest/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("1380013", PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_INVALID"));

        mockMvc.perform(post("/api/v1/guest/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(PHONE, "short1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));

        mockMvc.perform(post("/api/v1/guest/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s","confirmPassword":"Different-pass-2026",
                                 "acceptedAuthorization":true}
                                """.formatted(PHONE, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_CONFIRMATION_MISMATCH"));
    }

    @Test
    void refusesRegistrationWithoutAcceptingTheAuthorizationDocument() throws Exception {
        mockMvc.perform(post("/api/v1/guest/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s","confirmPassword":"%s",
                                 "acceptedAuthorization":false}
                                """.formatted(PHONE, PASSWORD, PASSWORD)))
                .andExpect(status().isBadRequest());

        assertThatNoAccountExists();
    }

    @Test
    void usesUniformInvalidCredentialErrorsForWrongPasswordAndUnknownPhone() throws Exception {
        registerGuest(mockMvc, PHONE, PASSWORD);

        assertInvalidLogin(PHONE, "Wrong-password-2026");
        assertInvalidLogin("13700137000", "Wrong-password-2026");
    }

    @Test
    void suspendsExistingSessionOnNextRequest() throws Exception {
        String token = registerGuest(mockMvc, PHONE, PASSWORD);

        accountMapper.update(Wrappers.<UserAccountEntity>lambdaUpdate()
                .eq(UserAccountEntity::getPhone, PHONE)
                .set(UserAccountEntity::getStatus, AccountStatus.SUSPENDED));

        mockMvc.perform(get("/api/v1/guest/profile/draft")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_INACTIVE"));
        mockMvc.perform(get("/api/v1/guest/profile/draft")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private void assertThatNoAccountExists() {
        org.assertj.core.api.Assertions.assertThat(
                        accountMapper.selectCount(Wrappers.<UserAccountEntity>lambdaQuery()))
                .isZero();
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

    private static String registrationBody(String phone, String password) {
        return """
                {"phone":"%s","password":"%s","confirmPassword":"%s","acceptedAuthorization":true}
                """.formatted(phone, password, password);
    }

    private String accessToken(MvcResult result) throws Exception {
        return new ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .get("data")
                .get("accessToken")
                .asText();
    }
}
