package com.love.archive.wechatpay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import com.love.archive.common.web.ApiException;
import com.love.archive.wechatpay.config.WechatPayProperties;
import com.love.archive.wechatpay.support.WechatHttpClient;
import com.love.archive.wechatpay.support.WechatPayCryptography;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class WechatPaymentChannelTest {

    private static final String API_V3_KEY = "0123456789abcdef0123456789abcdef";
    private static final String PLATFORM_KEY_ID = "PUB_KEY_ID_TEST";
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
        WechatPaymentChannel channel = new WechatPaymentChannel(
                emptyProvider(), new RecordingHttpClient(), blankProperties(), OBJECT_MAPPER);

        assertThat(channel.configured()).isFalse();
        assertNotConfigured(() -> channel.createOrder(
                new CreateOrderCommand("OTN-1", "建档服务", 100L, "openid-1")));
        assertNotConfigured(() -> channel.queryByOutTradeNo("OTN-1"));
        assertNotConfigured(() -> channel.verifyAndDecodeNotify(
                new NotifyPayload(PLATFORM_KEY_ID, "1", "n", "s", "{}")));
        assertNotConfigured(channel::appId);
        assertNotConfigured(() -> channel.authorizeUrl("state"));
        assertNotConfigured(() -> channel.resolveOpenId("code-1"));
    }

    @Test
    void createsJsapiOrderWithSignedRequestAndReturnsFrontendPayParameters() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueue(200, "{\"prepay_id\":\"wx-prepay-1\"}");
        WechatPaymentChannel channel = configuredChannel(httpClient);

        CreateOrderResult result = channel.createOrder(
                new CreateOrderCommand("OTN-CREATE-1", "婚恋档案库建档服务", 100L, "openid-payer-1"));

        RecordingHttpClient.Exchange exchange = httpClient.exchanges().getFirst();
        assertThat(exchange.method()).isEqualTo("POST");
        assertThat(exchange.url())
                .isEqualTo("https://pay.example.test/v3/pay/transactions/jsapi");
        assertThat(exchange.headers().get("Authorization"))
                .startsWith("WECHATPAY2-SHA256-RSA2048 ")
                .contains("mchid=\"1900000109\"")
                .contains("serial_no=\"SERIAL-1\"");
        assertThat(exchange.body())
                .contains("\"out_trade_no\":\"OTN-CREATE-1\"")
                .contains("\"total\":100")
                .contains("\"currency\":\"CNY\"")
                .contains("\"openid\":\"openid-payer-1\"")
                .contains("\"notify_url\":\"https://api.example.test/api/v1/public/online-payments/notifications\"");

        assertThat(result.prepayId()).isEqualTo("wx-prepay-1");
        assertThat(result.payParameters().appId()).isEqualTo("wx-app-1");
        assertThat(result.payParameters().packageValue()).isEqualTo("prepay_id=wx-prepay-1");
        assertThat(result.payParameters().signType()).isEqualTo("RSA");
        assertThat(result.payParameters().nonceStr()).hasSize(32);
        assertThat(verifyMerchantSignature(
                result.payParameters().appId() + "\n"
                        + result.payParameters().timeStamp() + "\n"
                        + result.payParameters().nonceStr() + "\n"
                        + "wx-prepay-1" + "\n",
                result.payParameters().paySign())).isTrue();
    }

    @Test
    void rejectsNonPositiveOrderAmountBeforeCallingTheChannel() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WechatPaymentChannel channel = configuredChannel(httpClient);

        assertThatThrownBy(() -> channel.createOrder(
                new CreateOrderCommand("OTN-ZERO", "建档服务", 0L, "openid-1")))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("PAYMENT_AMOUNT_INVALID"));
        assertThat(httpClient.exchanges()).isEmpty();
    }

    @Test
    void mapsAlreadyPaidChannelErrorToConflict() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueue(400, "{\"code\":\"ORDERPAID\",\"message\":\"订单已支付\"}");
        WechatPaymentChannel channel = configuredChannel(httpClient);

        assertThatThrownBy(() -> channel.createOrder(
                new CreateOrderCommand("OTN-PAID", "建档服务", 100L, "openid-1")))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("PAYMENT_ORDER_ALREADY_PAID"));
    }

    @Test
    void queriesOrderByOutTradeNoAndMapsMissingOrderToEmpty() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueue(200, """
                {"out_trade_no":"OTN-QUERY-1","transaction_id":"4200001","trade_state":"SUCCESS",
                 "success_time":"2026-08-18T10:00:00+08:00",
                 "amount":{"total":100,"payer_total":100},"payer":{"openid":"openid-payer-1"}}
                """);
        httpClient.enqueue(404, "{\"code\":\"ORDER_NOT_EXIST\"}");
        WechatPaymentChannel channel = configuredChannel(httpClient);

        Optional<PaymentResult> found = channel.queryByOutTradeNo("OTN-QUERY-1");
        Optional<PaymentResult> missing = channel.queryByOutTradeNo("OTN-QUERY-2");

        assertThat(found).isPresent();
        assertThat(found.get().paid()).isTrue();
        assertThat(found.get().transactionId()).isEqualTo("4200001");
        assertThat(found.get().paidAmountMinor()).isEqualTo(100L);
        assertThat(found.get().openid()).isEqualTo("openid-payer-1");
        assertThat(found.get().successTime()).isNotNull();
        assertThat(missing).isEmpty();
        assertThat(httpClient.exchanges().getFirst().url())
                .isEqualTo("https://pay.example.test/v3/pay/transactions/out-trade-no/OTN-QUERY-1"
                        + "?mchid=1900000109");
    }

    @Test
    void verifiesAndDecryptsSuccessNotification() {
        WechatPaymentChannel channel = configuredChannel(new RecordingHttpClient());
        String transaction = """
                {"out_trade_no":"OTN-NOTIFY-1","transaction_id":"4200002","trade_state":"SUCCESS",
                 "success_time":"2026-08-18T12:00:00+08:00",
                 "amount":{"total":100,"payer_total":100},"payer":{"openid":"openid-payer-2"}}
                """;
        String body = notificationBody(transaction);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "notify-nonce-1";

        PaymentResult result = channel.verifyAndDecodeNotify(new NotifyPayload(
                PLATFORM_KEY_ID, timestamp, nonce, platformSignature(timestamp, nonce, body), body));

        assertThat(result.outTradeNo()).isEqualTo("OTN-NOTIFY-1");
        assertThat(result.transactionId()).isEqualTo("4200002");
        assertThat(result.tradeState()).isEqualTo(TradeState.SUCCESS);
        assertThat(result.totalAmountMinor()).isEqualTo(100L);
        assertThat(result.paidAmountMinor()).isEqualTo(100L);
        assertThat(result.openid()).isEqualTo("openid-payer-2");
    }

    @Test
    void rejectsTamperedForgedStaleAndUnknownKeyNotifications() {
        WechatPaymentChannel channel = configuredChannel(new RecordingHttpClient());
        String body = notificationBody("""
                {"out_trade_no":"OTN-NOTIFY-2","transaction_id":"4200003","trade_state":"SUCCESS",
                 "amount":{"total":100,"payer_total":100},"payer":{"openid":"openid-payer-3"}}
                """);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "notify-nonce-2";
        String signature = platformSignature(timestamp, nonce, body);

        // 请求体被改动
        String tamperedBody = body.replace("\"id\":\"notify-1\"", "\"id\":\"notify-9\"");
        assertThat(tamperedBody).isNotEqualTo(body);
        assertSignatureInvalid(channel, new NotifyPayload(
                PLATFORM_KEY_ID, timestamp, nonce, signature, tamperedBody));
        // 签名不是平台密钥所签
        assertSignatureInvalid(channel, new NotifyPayload(
                PLATFORM_KEY_ID, timestamp, nonce, merchantSignature(timestamp, nonce, body), body));
        // 时间戳超出容忍窗口
        String staleTimestamp = String.valueOf(Instant.now().getEpochSecond() - 3_600);
        assertSignatureInvalid(channel, new NotifyPayload(
                PLATFORM_KEY_ID, staleTimestamp, nonce,
                platformSignature(staleTimestamp, nonce, body), body));
        // 平台公钥 ID 不匹配
        assertSignatureInvalid(channel, new NotifyPayload(
                "PUB_KEY_ID_OTHER", timestamp, nonce, signature, body));
        // 签名字段缺失
        assertSignatureInvalid(channel, new NotifyPayload(PLATFORM_KEY_ID, timestamp, nonce, "", body));
        // 资源密文与 API v3 密钥不匹配
        String wrongKeyBody = notificationBody(
                "{\"out_trade_no\":\"OTN-NOTIFY-3\"}", "fedcba9876543210fedcba9876543210");
        assertSignatureInvalid(channel, new NotifyPayload(
                PLATFORM_KEY_ID, timestamp, nonce,
                platformSignature(timestamp, nonce, wrongKeyBody), wrongKeyBody));
    }

    @Test
    void resolvesOpenIdFromAuthorizationCodeAndRejectsChannelErrors() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.enqueue(200, "{\"openid\":\"openid-oauth-1\",\"access_token\":\"tok\"}");
        httpClient.enqueue(200, "{\"errcode\":40029,\"errmsg\":\"invalid code\"}");
        WechatPaymentChannel channel = configuredChannel(httpClient);

        assertThat(channel.resolveOpenId("code-1")).isEqualTo("openid-oauth-1");
        assertThat(httpClient.exchanges().getFirst().url())
                .startsWith("https://oauth.example.test/sns/oauth2/access_token")
                .contains("code=code-1")
                .contains("grant_type=authorization_code");

        assertThatThrownBy(() -> channel.resolveOpenId("code-2"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("WECHAT_AUTHORIZATION_CODE_INVALID"));
    }

    @Test
    void buildsAuthorizeUrlFromServerSideRedirectUri() {
        WechatPaymentChannel channel = configuredChannel(new RecordingHttpClient());

        String url = channel.authorizeUrl("order-state-1");

        assertThat(url)
                .startsWith("https://open.weixin.qq.com/connect/oauth2/authorize")
                .contains("appid=wx-app-1")
                .contains("redirect_uri=https%3A%2F%2Fh5.example.test%2Fpay")
                .contains("scope=snsapi_base")
                .contains("state=order-state-1")
                .endsWith("#wechat_redirect");
    }

    private static void assertSignatureInvalid(WechatPaymentChannel channel, NotifyPayload payload) {
        assertThatThrownBy(() -> channel.verifyAndDecodeNotify(payload))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("PAYMENT_NOTIFY_SIGNATURE_INVALID"));
    }

    private static void assertNotConfigured(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.code()).isEqualTo("PAYMENT_CHANNEL_NOT_CONFIGURED");
            assertThat(exception.status().value()).isEqualTo(503);
        });
    }

    private static WechatPaymentChannel configuredChannel(WechatHttpClient httpClient) {
        WechatPayCryptography cryptography = new WechatPayCryptography(
                "1900000109",
                "SERIAL-1",
                pem("PRIVATE KEY", merchantKeyPair.getPrivate().getEncoded()),
                PLATFORM_KEY_ID,
                pem("PUBLIC KEY", platformKeyPair.getPublic().getEncoded()),
                API_V3_KEY,
                new SecureRandom());
        return new WechatPaymentChannel(
                fixedProvider(cryptography), httpClient, configuredProperties(), OBJECT_MAPPER);
    }

    private static WechatPayProperties configuredProperties() {
        return new WechatPayProperties(
                "wx-app-1",
                "wx-secret-1",
                "1900000109",
                "SERIAL-1",
                pem("PRIVATE KEY", merchantKeyPair.getPrivate().getEncoded()),
                API_V3_KEY,
                PLATFORM_KEY_ID,
                pem("PUBLIC KEY", platformKeyPair.getPublic().getEncoded()),
                "https://api.example.test/api/v1/public/online-payments/notifications",
                "https://h5.example.test/pay",
                "https://pay.example.test",
                "https://oauth.example.test");
    }

    private static WechatPayProperties blankProperties() {
        return new WechatPayProperties(
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static String notificationBody(String transactionJson) {
        return notificationBody(transactionJson, API_V3_KEY);
    }

    private static String notificationBody(String transactionJson, String apiV3Key) {
        String nonce = "abcdefghijkl";
        String associatedData = "transaction";
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8), "AES"),
                    new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            String ciphertext = Base64.getEncoder().encodeToString(
                    cipher.doFinal(transactionJson.getBytes(StandardCharsets.UTF_8)));
            return """
                    {"id":"notify-1","event_type":"TRANSACTION.SUCCESS","resource_type":"encrypt-resource",
                     "resource":{"algorithm":"AEAD_AES_256_GCM","original_type":"transaction",
                     "associated_data":"%s","nonce":"%s","ciphertext":"%s"}}
                    """.formatted(associatedData, nonce, ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String platformSignature(String timestamp, String nonce, String body) {
        return sign(platformKeyPair.getPrivate(), timestamp + "\n" + nonce + "\n" + body + "\n");
    }

    private static String merchantSignature(String timestamp, String nonce, String body) {
        return sign(merchantKeyPair.getPrivate(), timestamp + "\n" + nonce + "\n" + body + "\n");
    }

    private static String sign(java.security.PrivateKey key, String message) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(key);
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean verifyMerchantSignature(String message, String signatureBase64) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(merchantKeyPair.getPublic());
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(encoded)
                + "\n-----END " + label + "-----\n";
    }

    private static ObjectProvider<WechatPayCryptography> emptyProvider() {
        return new StubProvider(null);
    }

    private static ObjectProvider<WechatPayCryptography> fixedProvider(WechatPayCryptography value) {
        return new StubProvider(value);
    }

    private record StubProvider(WechatPayCryptography value)
            implements ObjectProvider<WechatPayCryptography> {

        @Override
        public WechatPayCryptography getIfAvailable() {
            return value;
        }

        @Override
        public WechatPayCryptography getObject() {
            if (value == null) {
                throw new org.springframework.beans.factory.NoSuchBeanDefinitionException(
                        WechatPayCryptography.class);
            }
            return value;
        }

        @Override
        public WechatPayCryptography getObject(Object... args) {
            return getObject();
        }

        @Override
        public WechatPayCryptography getIfUnique() {
            return value;
        }
    }

    private static final class RecordingHttpClient implements WechatHttpClient {

        private final List<Exchange> exchanges = new ArrayList<>();
        private final List<HttpTextResponse> responses = new ArrayList<>();

        void enqueue(int statusCode, String body) {
            responses.add(new HttpTextResponse(statusCode, body));
        }

        List<Exchange> exchanges() {
            return exchanges;
        }

        @Override
        public HttpTextResponse send(String httpMethod, String url, Map<String, String> headers, String body) {
            exchanges.add(new Exchange(httpMethod, url, Map.copyOf(headers), body));
            if (responses.isEmpty()) {
                throw new IllegalStateException("没有预置响应: " + url);
            }
            return responses.removeFirst();
        }

        record Exchange(String method, String url, Map<String, String> headers, String body) {
        }
    }
}
