package com.love.archive.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.PlatformApiApplication;
import com.love.archive.common.security.SensitiveValueProtector;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.domain.IdentityProvider;
import com.love.archive.identity.domain.MembershipTier;
import com.love.archive.identity.domain.RegistrationChannel;
import com.love.archive.identity.persistence.ExternalIdentityEntity;
import com.love.archive.identity.persistence.ExternalIdentityMapper;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.PhoneProtector;
import com.love.archive.payment.domain.RegistrationTokenStatus;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.PaymentRecordMapper;
import com.love.archive.payment.persistence.RegistrationTokenEntity;
import com.love.archive.payment.persistence.RegistrationTokenMapper;
import com.love.archive.testsupport.ApiIntegrationTest;
import com.love.archive.testsupport.WechatPayTestSupport;
import com.love.archive.testsupport.WechatPayTestSupport.FakeChannelHttpClient;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        classes = {PlatformApiApplication.class, OnlineRegistrationApiTest.FakeChannelConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class OnlineRegistrationApiTest extends ApiIntegrationTest {

    private static final String OPENID = "oPayer_register_1";
    private static final String PHONE = "13800138000";
    private static final String PASSWORD = "online-pass-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private FakeChannelHttpClient channelHttpClient;
    @Autowired private UserAccountMapper userAccountMapper;
    @Autowired private ExternalIdentityMapper externalIdentityMapper;
    @Autowired private PaymentRecordMapper paymentRecordMapper;
    @Autowired private RegistrationTokenMapper registrationTokenMapper;
    @Autowired private PhoneProtector phoneProtector;
    @Autowired private SensitiveValueProtector protector;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redis;

    @DynamicPropertySource
    static void registerChannel(DynamicPropertyRegistry registry) {
        WechatPayTestSupport.registerChannelProperties(registry);
        registry.add("app.payment.online.registration-amount-minor", () -> "59900");
        registry.add("app.membership.svip-threshold-minor", () -> "59900");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeChannelConfiguration {

        @Bean
        @Primary
        FakeChannelHttpClient fakeChannelHttpClient() {
            return new FakeChannelHttpClient();
        }
    }

    @BeforeEach
    void cleanState() {
        resetDatabase();
        channelHttpClient.reset();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void registersActiveVipAccountBindsOpenIdAndConsumesTheOrder() throws Exception {
        String token = paidRegistrationToken("4300000001");

        MvcResult result = register(token, PHONE, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountId").isNumber())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        long accountId = Long.parseLong(readData(result, "accountId"));
        UserAccountEntity account = userAccountMapper.selectById(accountId);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getRegistrationChannel()).isEqualTo(RegistrationChannel.WECHAT_ONLINE);
        assertThat(account.getCreatedByAdminId()).isNull();
        assertThat(account.getPasswordHash()).isNotBlank().doesNotContain(PASSWORD);
        assertThat(account.getActivatedAt()).isNotNull();
        assertThat(phoneProtector.decrypt(account.getPhoneCiphertext())).isEqualTo(PHONE);

        // 建档注册即 VIP，本次支付恰好达到阈值因此直接升 SVIP。
        assertThat(account.getMembershipCreditMinor()).isEqualTo(59_900L);
        assertThat(account.getMembershipTier()).isEqualTo(MembershipTier.SVIP);

        ExternalIdentityEntity identity = externalIdentityMapper.selectOne(
                Wrappers.<ExternalIdentityEntity>lambdaQuery()
                        .eq(ExternalIdentityEntity::getUserAccountId, accountId));
        assertThat(identity.getProvider()).isEqualTo(IdentityProvider.WECHAT);
        assertThat(identity.getSubjectHmac()).isEqualTo(protector.hmac("wechat:openid", OPENID));
        assertThat(protector.decrypt("wechat:openid", identity.getSubjectCiphertext())).isEqualTo(OPENID);

        PaymentRecordEntity payment = paymentRecordMapper.selectOne(
                Wrappers.<PaymentRecordEntity>lambdaQuery()
                        .eq(PaymentRecordEntity::getUserAccountId, accountId));
        assertThat(payment.getRegistered()).isTrue();
        assertThat(payment.getMembershipCreditMinor()).isEqualTo(59_900L);

        assertThat(statusOf(token)).isEqualTo(RegistrationTokenStatus.USED);
        assertThat(registrationTokenMapper.selectOne(
                Wrappers.<RegistrationTokenEntity>lambdaQuery()
                        .eq(RegistrationTokenEntity::getTokenHmac,
                                protector.hmac("registration:token", token)))
                .getUserAccountId()).isEqualTo(accountId);
    }

    @Test
    void theNewAccountCanLogInAndReadItsMembership() throws Exception {
        String token = paidRegistrationToken("4300000002");
        register(token, PHONE, PASSWORD).andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/v1/guest/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(PHONE, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = readData(login, "accessToken");

        mockMvc.perform(get("/api/v1/guest/membership").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("SVIP"))
                .andExpect(jsonPath("$.data.creditMinor").value(59900))
                .andExpect(jsonPath("$.data.svipThresholdMinor").value(59900))
                .andExpect(jsonPath("$.data.creditToNextTierMinor").value(0));
    }

    @Test
    void rejectsUnknownRegistrationToken() throws Exception {
        register("no-such-token", PHONE, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_INVALID"));

        assertThat(userAccountMapper.selectCount(null)).isZero();
    }

    @Test
    void rejectsExpiredRegistrationToken() throws Exception {
        String token = paidRegistrationToken("4300000003");
        // 令牌不能「出生即过期」（ck_registration_token_expiry），因此把两个时间戳一起前移。
        OffsetDateTime now = OffsetDateTime.now();
        registrationTokenMapper.update(Wrappers.<RegistrationTokenEntity>lambdaUpdate()
                .eq(RegistrationTokenEntity::getTokenHmac, protector.hmac("registration:token", token))
                .set(RegistrationTokenEntity::getCreatedAt, now.minusHours(2))
                .set(RegistrationTokenEntity::getExpiresAt, now.minusHours(1)));

        register(token, PHONE, PASSWORD)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_EXPIRED"));

        assertThat(userAccountMapper.selectCount(null)).isZero();
    }

    @Test
    void rejectsReuseOfAConsumedRegistrationToken() throws Exception {
        String token = paidRegistrationToken("4300000004");
        register(token, PHONE, PASSWORD).andExpect(status().isCreated());

        register(token, "13900139000", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_USED"));

        assertThat(userAccountMapper.selectCount(null)).isOne();
        assertThat(paymentRecordMapper.selectCount(null)).isOne();
    }

    @Test
    void rejectsRegistrationTokenSupersededByAReissue() throws Exception {
        String outTradeNo = paidOrder("4300000005");
        String firstToken = issueToken(outTradeNo);
        String secondToken = issueToken(outTradeNo);

        register(firstToken, PHONE, PASSWORD)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_EXPIRED"));
        register(secondToken, PHONE, PASSWORD).andExpect(status().isCreated());
    }

    @Test
    void refusesToRegisterTwiceForTheSameOrder() throws Exception {
        String outTradeNo = paidOrder("4300000006");
        register(issueToken(outTradeNo), PHONE, PASSWORD).andExpect(status().isCreated());

        mockMvc.perform(post(
                        "/api/v1/public/online-payments/orders/{outTradeNo}/registration-tokens", outTradeNo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_ALREADY_COMPLETED"));
    }

    @Test
    void rejectsDuplicatePhoneAndRollsBackTheWholeRegistration() throws Exception {
        String firstToken = paidRegistrationToken("4300000007");
        register(firstToken, PHONE, PASSWORD).andExpect(status().isCreated());

        String secondOrder = paidOrder("4300000008", "oPayer_register_2");
        register(issueToken(secondOrder), "138 0013 8000", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));

        assertThat(userAccountMapper.selectCount(null)).isOne();
        assertThat(externalIdentityMapper.selectCount(null)).isOne();
        assertThat(statusOfHmac(unusedTokenHmacFor(secondOrder)))
                .isEqualTo(RegistrationTokenStatus.UNUSED);
    }

    @Test
    void rejectsRebindingAWechatAccountAlreadyLinkedToAnotherAccount() throws Exception {
        register(paidRegistrationToken("4300000009"), PHONE, PASSWORD).andExpect(status().isCreated());

        // 同一 openid 的第二个订单：账号可以建，但 openid 已被占用，整笔注册回滚。
        String secondOrder = paidOrder("4300000010", OPENID);
        register(issueToken(secondOrder), "13900139000", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WECHAT_ACCOUNT_ALREADY_BOUND"));

        assertThat(userAccountMapper.selectCount(null)).isOne();
        assertThat(externalIdentityMapper.selectCount(null)).isOne();
    }

    @Test
    void enforcesPasswordPolicyAndPhoneFormatBeforeConsumingTheToken() throws Exception {
        String token = paidRegistrationToken("4300000011");

        register(token, PHONE, "short1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
        register(token, "12345", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_INVALID"));

        assertThat(statusOf(token)).isEqualTo(RegistrationTokenStatus.UNUSED);
        assertThat(userAccountMapper.selectCount(null)).isZero();
    }

    @Test
    void rejectsRegistrationWhenTheOrderIsNotPaidYet() throws Exception {
        channelHttpClient.nextOpenId(OPENID);
        channelHttpClient.nextPrepayId("wx-prepay-unpaid");
        String outTradeNo = createOrder();

        mockMvc.perform(post(
                        "/api/v1/public/online-payments/orders/{outTradeNo}/registration-tokens", outTradeNo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_ORDER_NOT_PAID"));

        assertThat(userAccountMapper.selectCount(null)).isZero();
    }

    private ResultActions register(String token, String phone, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/public/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"registrationToken":"%s","phone":"%s","password":"%s"}
                        """.formatted(token, phone, password)));
    }

    private String paidRegistrationToken(String transactionId) throws Exception {
        return issueToken(paidOrder(transactionId));
    }

    private String paidOrder(String transactionId) throws Exception {
        return paidOrder(transactionId, OPENID);
    }

    private String paidOrder(String transactionId, String openid) throws Exception {
        channelHttpClient.nextOpenId(openid);
        channelHttpClient.nextPrepayId("wx-prepay-" + transactionId);
        String outTradeNo = createOrder();
        String body = WechatPayTestSupport.successNotificationBody(
                outTradeNo, transactionId, openid, 59_900L, 59_900L);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-" + transactionId;
        mockMvc.perform(post("/api/v1/public/online-payments/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Wechatpay-Serial", WechatPayTestSupport.PLATFORM_KEY_ID)
                        .header("Wechatpay-Timestamp", timestamp)
                        .header("Wechatpay-Nonce", nonce)
                        .header("Wechatpay-Signature",
                                WechatPayTestSupport.platformSignature(timestamp, nonce, body))
                        .content(body))
                .andExpect(status().isOk());
        return outTradeNo;
    }

    private String createOrder() throws Exception {
        return readData(mockMvc.perform(post("/api/v1/public/online-payments/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"authorizationCode":"code-1","authorizationDocumentVersion":"v0.3"}
                                """))
                .andExpect(status().isCreated())
                .andReturn(), "outTradeNo");
    }

    private String issueToken(String outTradeNo) throws Exception {
        return readData(mockMvc.perform(post(
                        "/api/v1/public/online-payments/orders/{outTradeNo}/registration-tokens", outTradeNo))
                .andExpect(status().isCreated())
                .andReturn(), "token");
    }

    /** 待用令牌的明文签发后不可回溯，这里按订单取它的 HMAC。 */
    private String unusedTokenHmacFor(String outTradeNo) {
        RegistrationTokenEntity token = registrationTokenMapper.selectOne(
                Wrappers.<RegistrationTokenEntity>lambdaQuery()
                        .eq(RegistrationTokenEntity::getOutTradeNo, outTradeNo)
                        .eq(RegistrationTokenEntity::getStatus, RegistrationTokenStatus.UNUSED));
        return token == null ? null : token.getTokenHmac();
    }

    private RegistrationTokenStatus statusOf(String rawToken) {
        return statusOfHmac(protector.hmac("registration:token", rawToken));
    }

    private RegistrationTokenStatus statusOfHmac(String tokenHmac) {
        if (tokenHmac == null) {
            return null;
        }
        RegistrationTokenEntity token = registrationTokenMapper.selectOne(
                Wrappers.<RegistrationTokenEntity>lambdaQuery()
                        .eq(RegistrationTokenEntity::getTokenHmac, tokenHmac));
        return token == null ? null : token.getStatus();
    }

    private String readData(MvcResult result, String field) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path(field).asString();
    }
}
