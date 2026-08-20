package com.love.archive.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.love.archive.PlatformApiApplication;
import com.love.archive.common.security.SensitiveValueProtector;
import com.love.archive.payment.domain.PaymentChannelType;
import com.love.archive.payment.domain.RegistrationTokenStatus;
import com.love.archive.payment.domain.WechatOrderStatus;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.PaymentRecordMapper;
import com.love.archive.payment.persistence.RegistrationTokenEntity;
import com.love.archive.payment.persistence.RegistrationTokenMapper;
import com.love.archive.payment.persistence.WechatPaymentOrderEntity;
import com.love.archive.payment.persistence.WechatPaymentOrderMapper;
import com.love.archive.testsupport.ApiIntegrationTest;
import com.love.archive.testsupport.WechatPayTestSupport;
import com.love.archive.testsupport.WechatPayTestSupport.FakeChannelHttpClient;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// 父类显式声明了 classes，因此嵌套 @TestConfiguration 不会被自动发现，这里一并列出。
@SpringBootTest(
        classes = {PlatformApiApplication.class, OnlinePaymentApiTest.FakeChannelConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class OnlinePaymentApiTest extends ApiIntegrationTest {

    private static final String OPENID = "oPayer_online_1";

    @Autowired private MockMvc mockMvc;
    @Autowired private FakeChannelHttpClient channelHttpClient;
    @Autowired private WechatPaymentOrderMapper orderMapper;
    @Autowired private PaymentRecordMapper paymentRecordMapper;
    @Autowired private RegistrationTokenMapper registrationTokenMapper;
    @Autowired private SensitiveValueProtector protector;
    @Autowired private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerChannel(DynamicPropertyRegistry registry) {
        WechatPayTestSupport.registerChannelProperties(registry);
        registry.add("app.payment.online.registration-amount-minor", () -> "100");
        registry.add("app.payment.online.registration-token-ttl", () -> "30m");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeChannelConfiguration {

        /** 假客户端本身就是 WechatHttpClient，标为 @Primary 覆盖生产实现。 */
        @Bean
        @org.springframework.context.annotation.Primary
        FakeChannelHttpClient fakeChannelHttpClient() {
            return new FakeChannelHttpClient();
        }
    }

    @BeforeEach
    void cleanState() {
        resetDatabase();
        channelHttpClient.reset();
    }

    @Test
    void exposesChannelSettingsWithoutLeakingSecrets() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/public/online-payments/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelType").value("WECHAT_JSAPI"))
                .andExpect(jsonPath("$.data.amountMinor").value(100))
                .andExpect(jsonPath("$.data.authorizeUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.state").isNotEmpty())
                .andReturn();

        String payload = result.getResponse().getContentAsString();
        assertThat(payload)
                .contains("scope=snsapi_base")
                .contains("redirect_uri=https%3A%2F%2Fh5.example.test")
                .doesNotContain("wx-secret-it")
                .doesNotContain(WechatPayTestSupport.API_V3_KEY)
                .doesNotContain("PRIVATE KEY");
    }

    @Test
    void createsOrderFromAuthorizationCodeAndStoresOpenIdEncrypted() throws Exception {
        channelHttpClient.nextOpenId(OPENID);
        channelHttpClient.nextPrepayId("wx-prepay-it-1");

        String outTradeNo = createOrder();

        WechatPaymentOrderEntity order = requireOrder(outTradeNo);
        assertThat(order.getStatus()).isEqualTo(WechatOrderStatus.CREATED);
        assertThat(order.getAmountMinor()).isEqualTo(100L);
        assertThat(order.getPrepayId()).isEqualTo("wx-prepay-it-1");
        assertThat(order.getPresentedAuthorizationDocumentId()).isNotNull();
        assertThat(order.getPaymentRecordId()).isNull();
        assertThat(new String(order.getOpenidCiphertext(), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain(OPENID);
        assertThat(protector.decrypt("wechat:openid", order.getOpenidCiphertext())).isEqualTo(OPENID);
        assertThat(paymentRecordMapper.selectCount(null)).isZero();
    }

    @Test
    void rejectsOrderCreationWhenAuthorizationCodeIsInvalid() throws Exception {
        channelHttpClient.nextOauthFailure();

        mockMvc.perform(post("/api/v1/public/online-payments/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"authorizationCode":"bad-code","authorizationDocumentVersion":"v0.3"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WECHAT_AUTHORIZATION_CODE_INVALID"));

        assertThat(orderMapper.selectCount(null)).isZero();
    }

    @Test
    void rejectsOrderCreationForUnknownAuthorizationDocumentVersion() throws Exception {
        channelHttpClient.nextOpenId(OPENID);

        mockMvc.perform(post("/api/v1/public/online-payments/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"authorizationCode":"code-1","authorizationDocumentVersion":"v9.9"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DOCUMENT_NOT_FOUND"));

        assertThat(orderMapper.selectCount(null)).isZero();
    }

    @Test
    void settlesVerifiedNotificationIntoOrderAndPaymentRecord() throws Exception {
        String outTradeNo = createPaidOrder("4200000001");

        WechatPaymentOrderEntity order = requireOrder(outTradeNo);
        assertThat(order.getStatus()).isEqualTo(WechatOrderStatus.PAID);
        assertThat(order.getPaidAmountMinor()).isEqualTo(100L);
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(order.getPaymentRecordId()).isNotNull();
        assertThat(order.getTransactionIdHmac())
                .isEqualTo(protector.hmac("wechat:transaction", "4200000001"));

        PaymentRecordEntity payment = paymentRecordMapper.selectById(order.getPaymentRecordId());
        assertThat(payment.getPaymentChannel()).isEqualTo(PaymentChannelType.WECHAT_JSAPI);
        assertThat(payment.getOutTradeNo()).isEqualTo(outTradeNo);
        assertThat(payment.getPaymentReference()).isEqualTo(outTradeNo);
        assertThat(payment.getAmountMinor()).isEqualTo(100L);
        assertThat(payment.getPaidAmountMinor()).isEqualTo(100L);
        assertThat(payment.getUserAccountId()).isNull();
        assertThat(payment.getOperatorAdminId()).isNull();
        assertThat(payment.getRegistered()).isFalse();
        assertThat(payment.getMembershipCreditMinor()).isZero();
        assertThat(payment.getPresentedAuthorizationDocumentId()).isNotNull();
        assertThat(protector.decrypt("wechat:transaction", payment.getTransactionIdCiphertext()))
                .isEqualTo("4200000001");
    }

    @Test
    void repeatedNotificationIsIdempotent() throws Exception {
        channelHttpClient.nextOpenId(OPENID);
        channelHttpClient.nextPrepayId("wx-prepay-it-2");
        String outTradeNo = createOrder();
        String body = WechatPayTestSupport.successNotificationBody(
                outTradeNo, "4200000002", OPENID, 100L, 100L);

        postNotification(body).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"));
        postNotification(body).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"));

        assertThat(paymentRecordMapper.selectCount(null)).isOne();
        assertThat(requireOrder(outTradeNo).getStatus()).isEqualTo(WechatOrderStatus.PAID);
    }

    @Test
    void rejectsNotificationWhoseAmountDoesNotMatchTheServerSideOrder() throws Exception {
        channelHttpClient.nextOpenId(OPENID);
        channelHttpClient.nextPrepayId("wx-prepay-it-3");
        String outTradeNo = createOrder();

        postNotification(WechatPayTestSupport.successNotificationBody(
                outTradeNo, "4200000003", OPENID, 1L, 1L))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("FAIL"));

        assertThat(requireOrder(outTradeNo).getStatus()).isEqualTo(WechatOrderStatus.CREATED);
        assertThat(paymentRecordMapper.selectCount(null)).isZero();
    }

    @Test
    void rejectsNotificationWhosePayerDoesNotMatchTheOrder() throws Exception {
        channelHttpClient.nextOpenId(OPENID);
        channelHttpClient.nextPrepayId("wx-prepay-it-4");
        String outTradeNo = createOrder();

        postNotification(WechatPayTestSupport.successNotificationBody(
                outTradeNo, "4200000004", "oSomeoneElse", 100L, 100L))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("FAIL"));

        assertThat(requireOrder(outTradeNo).getStatus()).isEqualTo(WechatOrderStatus.CREATED);
        assertThat(paymentRecordMapper.selectCount(null)).isZero();
    }

    @Test
    void rejectsForgedNotificationSignature() throws Exception {
        channelHttpClient.nextOpenId(OPENID);
        channelHttpClient.nextPrepayId("wx-prepay-it-5");
        String outTradeNo = createOrder();
        String body = WechatPayTestSupport.successNotificationBody(
                outTradeNo, "4200000005", OPENID, 100L, 100L);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        mockMvc.perform(post("/api/v1/public/online-payments/notifications/wechat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Wechatpay-Serial", WechatPayTestSupport.PLATFORM_KEY_ID)
                        .header("Wechatpay-Timestamp", timestamp)
                        .header("Wechatpay-Nonce", "nonce-forged")
                        .header("Wechatpay-Signature",
                                WechatPayTestSupport.forgedSignature(timestamp, "nonce-forged", body))
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("FAIL"));

        assertThat(requireOrder(outTradeNo).getStatus()).isEqualTo(WechatOrderStatus.CREATED);
        assertThat(paymentRecordMapper.selectCount(null)).isZero();
    }

    @Test
    void notificationForUnknownOrderFails() throws Exception {
        postNotification(WechatPayTestSupport.successNotificationBody(
                "unknown-out-trade-no", "4200000006", OPENID, 100L, 100L))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("FAIL"));
    }

    @Test
    void statusQueryFallsBackToChannelWhenNotificationNeverArrived() throws Exception {
        channelHttpClient.nextOpenId(OPENID);
        channelHttpClient.nextPrepayId("wx-prepay-it-6");
        String outTradeNo = createOrder();
        channelHttpClient.nextQueryResult(200, """
                {"out_trade_no":"%s","transaction_id":"4200000007","trade_state":"SUCCESS",
                 "success_time":"2026-08-18T10:00:00+08:00",
                 "amount":{"total":100,"payer_total":100},"payer":{"openid":"%s"}}
                """.formatted(outTradeNo, OPENID));

        mockMvc.perform(get("/api/v1/public/online-payments/orders/{outTradeNo}", outTradeNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.registered").value(false));

        assertThat(paymentRecordMapper.selectCount(null)).isOne();
    }

    @Test
    void statusQueryReportsUnknownOrderAsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/public/online-payments/orders/{outTradeNo}", "no-such-order"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_ORDER_NOT_FOUND"));
    }

    @Test
    void issuesOneTimeRegistrationTokenOnlyAfterPaymentAndStoresHmacOnly() throws Exception {
        String outTradeNo = createPaidOrder("4200000008");

        MvcResult result = mockMvc.perform(post(
                        "/api/v1/public/online-payments/orders/{outTradeNo}/registration-tokens", outTradeNo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andReturn();

        String token = readData(result, "token");
        RegistrationTokenEntity stored = registrationTokenMapper.selectOne(
                Wrappers.<RegistrationTokenEntity>lambdaQuery()
                        .eq(RegistrationTokenEntity::getOutTradeNo, outTradeNo));
        assertThat(stored.getStatus()).isEqualTo(RegistrationTokenStatus.UNUSED);
        assertThat(stored.getTokenHmac())
                .isEqualTo(protector.hmac("registration:token", token))
                .isNotEqualTo(token);
        assertThat(stored.getExpiresAt()).isAfter(OffsetDateTime.now());
        assertThat(stored.getUserAccountId()).isNull();
    }

    @Test
    void reissuingRegistrationTokenInvalidatesThePreviousOne() throws Exception {
        String outTradeNo = createPaidOrder("4200000009");

        String firstToken = readData(mockMvc.perform(post(
                        "/api/v1/public/online-payments/orders/{outTradeNo}/registration-tokens", outTradeNo))
                .andExpect(status().isCreated())
                .andReturn(), "token");
        String secondToken = readData(mockMvc.perform(post(
                        "/api/v1/public/online-payments/orders/{outTradeNo}/registration-tokens", outTradeNo))
                .andExpect(status().isCreated())
                .andReturn(), "token");

        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThat(statusOf(firstToken)).isEqualTo(RegistrationTokenStatus.EXPIRED);
        assertThat(statusOf(secondToken)).isEqualTo(RegistrationTokenStatus.UNUSED);
    }

    @Test
    void refusesRegistrationTokenBeforePaymentAndForUnknownOrders() throws Exception {
        channelHttpClient.nextOpenId(OPENID);
        channelHttpClient.nextPrepayId("wx-prepay-it-10");
        String outTradeNo = createOrder();

        mockMvc.perform(post(
                        "/api/v1/public/online-payments/orders/{outTradeNo}/registration-tokens", outTradeNo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_ORDER_NOT_PAID"));

        mockMvc.perform(post(
                        "/api/v1/public/online-payments/orders/{outTradeNo}/registration-tokens", "nope-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_ORDER_NOT_FOUND"));
    }

    private String createOrder() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/public/online-payments/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"authorizationCode":"code-1","authorizationDocumentVersion":"v0.3"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amountMinor").value(100))
                .andExpect(jsonPath("$.data.payParameters.wechatJsapi.appId").value("wx-app-it"))
                .andExpect(jsonPath("$.data.payParameters.wechatJsapi.paySign").isNotEmpty())
                .andReturn();
        return readData(result, "outTradeNo");
    }

    private String createPaidOrder(String transactionId) throws Exception {
        channelHttpClient.nextOpenId(OPENID);
        channelHttpClient.nextPrepayId("wx-prepay-" + transactionId);
        String outTradeNo = createOrder();
        postNotification(WechatPayTestSupport.successNotificationBody(
                outTradeNo, transactionId, OPENID, 100L, 100L))
                .andExpect(status().isOk());
        return outTradeNo;
    }

    private org.springframework.test.web.servlet.ResultActions postNotification(String body) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-" + Math.abs(body.hashCode());
        return mockMvc.perform(post("/api/v1/public/online-payments/notifications/wechat")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Wechatpay-Serial", WechatPayTestSupport.PLATFORM_KEY_ID)
                .header("Wechatpay-Timestamp", timestamp)
                .header("Wechatpay-Nonce", nonce)
                .header("Wechatpay-Signature",
                        WechatPayTestSupport.platformSignature(timestamp, nonce, body))
                .content(body));
    }

    private RegistrationTokenStatus statusOf(String rawToken) {
        RegistrationTokenEntity token = registrationTokenMapper.selectOne(
                Wrappers.<RegistrationTokenEntity>lambdaQuery()
                        .eq(RegistrationTokenEntity::getTokenHmac,
                                protector.hmac("registration:token", rawToken)));
        return token == null ? null : token.getStatus();
    }

    private WechatPaymentOrderEntity requireOrder(String outTradeNo) {
        WechatPaymentOrderEntity order = orderMapper.selectOne(
                Wrappers.<WechatPaymentOrderEntity>lambdaQuery()
                        .eq(WechatPaymentOrderEntity::getOutTradeNo, outTradeNo));
        assertThat(order).isNotNull();
        return order;
    }

    private String readData(MvcResult result, String field) throws Exception {
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return payload.path("data").path(field).asString();
    }
}
