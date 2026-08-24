package com.love.archive.xpay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import com.love.archive.common.web.ApiException;
import com.love.archive.payment.application.CreateOrderCommand;
import com.love.archive.payment.application.CreateOrderResult;
import com.love.archive.payment.application.NotifyPayload;
import com.love.archive.payment.application.PaymentResult;
import com.love.archive.payment.domain.PaymentChannelType;
import com.love.archive.xpay.config.XpayProperties;
import com.love.archive.xpay.support.XpayCryptography;
import com.love.archive.xpay.support.XpayHttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class XpayPaymentChannelTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static KeyPair merchantKeyPair;
    private static KeyPair platformKeyPair;

    @BeforeAll
    static void generateKeys() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        merchantKeyPair = generator.generateKeyPair();
        platformKeyPair = generator.generateKeyPair();
    }

    @Test
    void reportsNotConfiguredWhenCredentialsAreMissing() {
        XpayPaymentChannel channel = new XpayPaymentChannel(
                new StubProvider(null), new RecordingHttpClient(), configuredProperties(), OBJECT_MAPPER);

        assertThat(channel.configured()).isFalse();
        assertNotConfigured(() -> channel.createOrder(
                new CreateOrderCommand("OTN-1", "建档服务", 100L, null)));
        assertNotConfigured(() -> channel.queryByOutTradeNo("OTN-1"));
        assertNotConfigured(() -> channel.verifyAndDecodeNotify(
                new NotifyPayload(Map.of(), Map.of(), null)));
    }

    @Test
    void createsSubmitOrderAndReturnsCashierLocation() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueueRedirect("https://xpay.example.test/pay/20260823225910918724");
        XpayPaymentChannel channel = configuredChannel(httpClient);

        CreateOrderResult result = channel.createOrder(
                new CreateOrderCommand("OTN-CREATE-1", "婚恋档案库建档服务", 100L, null));

        Map<String, String> form = httpClient.lastForm();
        assertThat(httpClient.lastUrl()).endsWith("/api/pay/submit");
        assertThat(form.get("pid")).isEqualTo("10192");
        assertThat(form.get("type")).isEqualTo("alipay");
        assertThat(form).doesNotContainKey("method");
        assertThat(form.get("out_trade_no")).isEqualTo("OTN-CREATE-1");
        assertThat(form.get("money")).isEqualTo("1.00");
        assertThat(form.get("sign_type")).isEqualTo("RSA");
        assertThat(form).containsKey("timestamp").containsKey("sign");
        assertThat(verifyMerchantSignature(form)).isTrue();

        assertThat(result.payParameters().channelType()).isEqualTo(PaymentChannelType.XPAY_ALIPAY);
        assertThat(result.payParameters().jumpUrl())
                .isEqualTo("https://xpay.example.test/pay/20260823225910918724");
    }

    /**
     * 收银台地址的末段就是渠道侧订单号，而页面支付的 302 没有响应体——这是下单当场
     * 唯一能拿到它的地方。拿不到就意味着一笔没付成的订单在库里没有任何可对账的编号。
     */
    @Test
    void picksUpTheChannelTradeNoFromTheCashierLocation() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueueRedirect("/pay/20260823225910918724");
        XpayPaymentChannel channel = configuredChannel(httpClient);

        CreateOrderResult result = channel.createOrder(
                new CreateOrderCommand("OTN-CREATE-2", "建档服务", 100L, null));

        assertThat(result.channelReference()).isEqualTo("20260823225910918724");
        // 相对路径要补上站点 origin 才是能跳的完整地址。
        assertThat(result.payParameters().jumpUrl())
                .isEqualTo("https://xpay.example.test/pay/20260823225910918724");
    }

    /** 末段不是单号形状时宁可留空，也不要把 /pay、/cashier 这类路径片段当订单号存进去。 */
    @Test
    void leavesTheChannelTradeNoEmptyWhenTheLocationCarriesNone() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueueRedirect("https://cashier.example/pay?order=1");
        XpayPaymentChannel channel = configuredChannel(httpClient);

        CreateOrderResult result = channel.createOrder(
                new CreateOrderCommand("OTN-CREATE-3", "建档服务", 100L, null));

        assertThat(result.channelReference()).isNull();
        assertThat(result.payParameters().jumpUrl()).isEqualTo("https://cashier.example/pay?order=1");
    }

    /**
     * 查单响应的业务字段包在 {@code data} 里——这是 {@code XPAY-API.md}「查询订单」
     * 给的真实形状。原来的实现把根节点直接交给解析函数，于是 out_trade_no / status / money
     * 全取不到，查单成功也会解析成「未支付」，补偿查单这条安全网形同不存在。
     */
    @Test
    void queriesPaidOrderFromTheDataEnvelope() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueue(200, """
                {"code":0,"msg":"success","data":{
                  "pid":10192,"type":"alipay","out_trade_no":"OTN-Q-1",
                  "trade_no":"20260819205920933269","api_trade_no":"20260819205920933269",
                  "money":"0.01","status":"1","buyer":"buyer-1","trade_status":"TRADE_SUCCESS"}}
                """);
        XpayPaymentChannel channel = configuredChannel(httpClient);

        Optional<PaymentResult> result = channel.queryByOutTradeNo("OTN-Q-1");

        assertThat(result).isPresent();
        assertThat(result.get().paid()).isTrue();
        assertThat(result.get().outTradeNo()).isEqualTo("OTN-Q-1");
        assertThat(result.get().transactionId()).isEqualTo("20260819205920933269");
        assertThat(result.get().totalAmountMinor()).isEqualTo(1L);
        assertThat(result.get().payer()).isEqualTo("buyer-1");
        assertThat(result.get().successTime()).isNotNull();
    }

    /** data 里只有 trade_status 没有 status 时也要认出已支付（文档写的是「或」）。 */
    @Test
    void treatsTradeSuccessAsPaidEvenWithoutStatusField() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueue(200, """
                {"code":0,"msg":"success","data":{
                  "out_trade_no":"OTN-Q-2","trade_no":"T2","money":"99.00",
                  "trade_status":"TRADE_SUCCESS"}}
                """);
        XpayPaymentChannel channel = configuredChannel(httpClient);

        Optional<PaymentResult> result = channel.queryByOutTradeNo("OTN-Q-2");

        assertThat(result).isPresent();
        assertThat(result.get().paid()).isTrue();
        assertThat(result.get().totalAmountMinor()).isEqualTo(9900L);
    }

    /** 未支付的订单必须解析成 paid=false，绝不能因为查单成功就当成已付。 */
    @Test
    void queriesUnpaidOrderAsNotPaid() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueue(200, """
                {"code":0,"msg":"success","data":{
                  "out_trade_no":"OTN-Q-3","money":"99.00","status":"0","trade_status":"WAIT_BUYER_PAY"}}
                """);
        XpayPaymentChannel channel = configuredChannel(httpClient);

        Optional<PaymentResult> result = channel.queryByOutTradeNo("OTN-Q-3");

        assertThat(result).isPresent();
        assertThat(result.get().paid()).isFalse();
        assertThat(result.get().successTime()).isNull();
    }

    /**
     * 订单在网关那边过期后查单回的是 {@code code=1 / 没有找到订单信息}（线上实测）。
     * 这不该冒成 502「支付渠道查询失败」——那句话会让用户以为系统坏了，而真实答案是
     * 「这笔单子已经没了，重新下一笔」。返回空，让本地的过期状态去回答。
     */
    @Test
    void reportsNoResultWhenTheGatewayLostTheOrder() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueue(200, "{\"code\":1,\"msg\":\"没有找到订单信息\",\"status\":0}");
        XpayPaymentChannel channel = configuredChannel(httpClient);

        assertThat(channel.queryByOutTradeNo("OTN-MISSING")).isEmpty();
    }

    /** 网关自身拒绝时用的是 {@code message} 而不是 {@code msg}，同样只当「查不到」。 */
    @Test
    void reportsNoResultWhenTheGatewayRejectsTheRequest() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueue(200, "{\"code\":502,\"message\":\"必填sign\",\"data\":{},\"redirect\":\"\"}");
        XpayPaymentChannel channel = configuredChannel(httpClient);

        assertThat(channel.queryByOutTradeNo("OTN-REJECTED")).isEmpty();
    }

    /** 响应体压根不是 JSON 时仍要抛——那是真的坏了，不能和「订单不存在」混为一谈。 */
    @Test
    void stillFailsLoudlyWhenTheResponseIsNotJson() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueue(502, "<html><body>Bad Gateway</body></html>");
        XpayPaymentChannel channel = configuredChannel(httpClient);

        assertThatThrownBy(() -> channel.queryByOutTradeNo("OTN-BROKEN"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("PAYMENT_CHANNEL_RESPONSE_INVALID"));
    }

    /**
     * 网关**实测**回的是平铺形状（文档写的是包在 data 里），而且未支付时也带 trade_no——
     * 那往往是我们唯一能拿到渠道订单号的时机，必须解析得出来。
     */
    @Test
    void stillParsesTheFlatShape() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueue(200, """
                {"code":0,"msg":"success","trade_no":"2026081100002","out_trade_no":"OTN-Q-1",
                 "api_trade_no":"2026081100002","type":"alipay","status":1,"money":"1.00","buyer":"buyer-1"}
                """);
        XpayPaymentChannel channel = configuredChannel(httpClient);

        Optional<PaymentResult> result = channel.queryByOutTradeNo("OTN-Q-1");

        assertThat(result).isPresent();
        assertThat(result.get().paid()).isTrue();
        assertThat(result.get().outTradeNo()).isEqualTo("OTN-Q-1");
        assertThat(result.get().totalAmountMinor()).isEqualTo(100L);
    }

    /** 平铺形状 + 未支付：拿不到 paid，但 trade_no 要拿得到。 */
    @Test
    void carriesTheChannelTradeNoEvenWhenUnpaid() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueue(200, """
                {"code":0,"msg":"success","trade_no":"20260823225914438191",
                 "out_trade_no":"OTN-Q-4","type":"alipay","status":0,"money":"0.02",
                 "addtime":"2026-08-23 22:59:14"}
                """);
        XpayPaymentChannel channel = configuredChannel(httpClient);

        Optional<PaymentResult> result = channel.queryByOutTradeNo("OTN-Q-4");

        assertThat(result).isPresent();
        assertThat(result.get().paid()).isFalse();
        assertThat(result.get().transactionId()).isEqualTo("20260823225914438191");
    }

    @Test
    void verifiesSignedNotificationAndRejectsTampered() {
        XpayPaymentChannel channel = configuredChannel(new RecordingHttpClient());
        Map<String, String> params = new TreeMap<>();
        params.put("pid", "10192");
        params.put("trade_no", "2026081100003");
        params.put("out_trade_no", "OTN-N-1");
        params.put("type", "alipay");
        params.put("money", "1.00");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("sign_type", "RSA");
        params.put("sign", platformSignature(params));

        PaymentResult result = channel.verifyAndDecodeNotify(
                new NotifyPayload(Map.of(), params, null));

        assertThat(result.paid()).isTrue();
        assertThat(result.outTradeNo()).isEqualTo("OTN-N-1");
        assertThat(result.totalAmountMinor()).isEqualTo(100L);

        Map<String, String> tampered = new TreeMap<>(params);
        tampered.put("money", "9.99");
        assertThatThrownBy(() -> channel.verifyAndDecodeNotify(
                new NotifyPayload(Map.of(), tampered, null)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("PAYMENT_NOTIFY_SIGNATURE_INVALID"));
    }

    private static boolean verifyMerchantSignature(Map<String, String> params) {
        String signature = params.get("sign");
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(merchantKeyPair.getPublic());
            verifier.update(XpayCryptography.canonicalize(params).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String platformSignature(Map<String, String> params) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(platformKeyPair.getPrivate());
            signer.update(XpayCryptography.canonicalize(params).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void assertNotConfigured(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.code()).isEqualTo("PAYMENT_CHANNEL_NOT_CONFIGURED");
            assertThat(exception.status().value()).isEqualTo(503);
        });
    }

    private static XpayPaymentChannel configuredChannel(XpayHttpClient httpClient) {
        XpayCryptography cryptography = new XpayCryptography(
                pem("PRIVATE KEY", merchantKeyPair.getPrivate().getEncoded()),
                pem("PUBLIC KEY", platformKeyPair.getPublic().getEncoded()));
        return new XpayPaymentChannel(
                new StubProvider(cryptography), httpClient, configuredProperties(), OBJECT_MAPPER);
    }

    private static XpayProperties configuredProperties() {
        return new XpayProperties(
                "10192",
                pem("PRIVATE KEY", merchantKeyPair.getPrivate().getEncoded()),
                pem("PUBLIC KEY", platformKeyPair.getPublic().getEncoded()),
                "https://api.example.test/api/v1/public/payment-notifications/xpay",
                "https://h5.example.test/pay",
                "https://xpay.example.test");
    }

    private static String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(encoded)
                + "\n-----END " + label + "-----\n";
    }

    private record StubProvider(XpayCryptography value)
            implements ObjectProvider<XpayCryptography> {

        @Override
        public XpayCryptography getIfAvailable() {
            return value;
        }

        @Override
        public XpayCryptography getObject() {
            if (value == null) {
                throw new org.springframework.beans.factory.NoSuchBeanDefinitionException(
                        XpayCryptography.class);
            }
            return value;
        }

        @Override
        public XpayCryptography getObject(Object... args) {
            return getObject();
        }

        @Override
        public XpayCryptography getIfUnique() {
            return value;
        }
    }

    private static final class RecordingHttpClient implements XpayHttpClient {

        private final java.util.List<HttpTextResponse> responses = new java.util.ArrayList<>();
        private String lastUrl;
        private Map<String, String> lastForm;

        void enqueue(int statusCode, String body) {
            responses.add(new HttpTextResponse(statusCode, body, Map.of()));
        }

        void enqueueRedirect(String location) {
            responses.add(new HttpTextResponse(302, "Found", Map.of("location", location)));
        }

        String lastUrl() {
            return lastUrl;
        }

        Map<String, String> lastForm() {
            return lastForm;
        }

        @Override
        public HttpTextResponse postForm(String url, Map<String, String> form) {
            this.lastUrl = url;
            this.lastForm = Map.copyOf(form);
            if (responses.isEmpty()) {
                throw new IllegalStateException("没有预置响应: " + url);
            }
            return responses.removeFirst();
        }
    }
}
