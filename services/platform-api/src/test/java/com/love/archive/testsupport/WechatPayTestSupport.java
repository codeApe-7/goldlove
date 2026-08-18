package com.love.archive.testsupport;

import com.love.archive.wechatpay.support.WechatHttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * 线上支付集成测试脚手架：生成一次性商户/平台密钥对，把凭据注册进环境，
 * 并用可编程的假 HTTP 客户端替换出网调用。签名、验签与资源解密走真实实现。
 */
public final class WechatPayTestSupport {

    public static final String API_V3_KEY = "0123456789abcdef0123456789abcdef";
    public static final String PLATFORM_KEY_ID = "PUB_KEY_ID_IT";
    public static final String APP_ID = "wx-app-it";
    public static final String MERCHANT_ID = "1900000109";
    public static final String NOTIFY_URL =
            "https://api.example.test/api/v1/public/online-payments/notifications";
    public static final String OAUTH_REDIRECT_URI = "https://h5.example.test/pages/payment/index";

    private static final KeyPair MERCHANT_KEY_PAIR = generateKeyPair();
    private static final KeyPair PLATFORM_KEY_PAIR = generateKeyPair();

    private WechatPayTestSupport() {
    }

    public static void registerChannelProperties(DynamicPropertyRegistry registry) {
        registry.add("app.wechat.pay.app-id", () -> APP_ID);
        registry.add("app.wechat.pay.app-secret", () -> "wx-secret-it");
        registry.add("app.wechat.pay.merchant-id", () -> MERCHANT_ID);
        registry.add("app.wechat.pay.merchant-serial-number", () -> "SERIAL-IT");
        registry.add("app.wechat.pay.merchant-private-key",
                () -> pem("PRIVATE KEY", MERCHANT_KEY_PAIR.getPrivate().getEncoded()));
        registry.add("app.wechat.pay.api-v3-key", () -> API_V3_KEY);
        registry.add("app.wechat.pay.platform-public-key-id", () -> PLATFORM_KEY_ID);
        registry.add("app.wechat.pay.platform-public-key",
                () -> pem("PUBLIC KEY", PLATFORM_KEY_PAIR.getPublic().getEncoded()));
        registry.add("app.wechat.pay.notify-url", () -> NOTIFY_URL);
        registry.add("app.wechat.pay.oauth-redirect-uri", () -> OAUTH_REDIRECT_URI);
        registry.add("app.wechat.pay.api-base-url", () -> "https://pay.example.test");
        registry.add("app.wechat.pay.oauth-base-url", () -> "https://oauth.example.test");
    }

    /** 构造微信支付成功通知体（含 AEAD_AES_256_GCM 加密资源）。 */
    public static String successNotificationBody(
            String outTradeNo,
            String transactionId,
            String openid,
            long totalMinor,
            long payerTotalMinor) {
        return notificationBody("""
                {"out_trade_no":"%s","transaction_id":"%s","trade_state":"SUCCESS",
                 "trade_type":"JSAPI","bank_type":"OTHERS","success_time":"%s",
                 "amount":{"total":%d,"payer_total":%d,"currency":"CNY","payer_currency":"CNY"},
                 "payer":{"openid":"%s"},"mchid":"%s","appid":"%s"}
                """.formatted(
                outTradeNo,
                transactionId,
                Instant.now().atOffset(java.time.ZoneOffset.ofHours(8)),
                totalMinor,
                payerTotalMinor,
                openid,
                MERCHANT_ID,
                APP_ID));
    }

    public static String notificationBody(String transactionJson) {
        String nonce = "abcdefghijkl";
        String associatedData = "transaction";
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(API_V3_KEY.getBytes(StandardCharsets.UTF_8), "AES"),
                    new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            String ciphertext = Base64.getEncoder().encodeToString(
                    cipher.doFinal(transactionJson.getBytes(StandardCharsets.UTF_8)));
            return """
                    {"id":"notify-it","create_time":"%s","event_type":"TRANSACTION.SUCCESS",\
                    "resource_type":"encrypt-resource","summary":"支付成功",\
                    "resource":{"algorithm":"AEAD_AES_256_GCM","original_type":"transaction",\
                    "associated_data":"%s","nonce":"%s","ciphertext":"%s"}}\
                    """.formatted(Instant.now(), associatedData, nonce, ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 用平台私钥签名，模拟微信支付发出的合法回调。 */
    public static String platformSignature(String timestamp, String nonce, String body) {
        return sign(PLATFORM_KEY_PAIR.getPrivate(), timestamp + "\n" + nonce + "\n" + body + "\n");
    }

    /** 用商户私钥签名，模拟被伪造的回调。 */
    public static String forgedSignature(String timestamp, String nonce, String body) {
        return sign(MERCHANT_KEY_PAIR.getPrivate(), timestamp + "\n" + nonce + "\n" + body + "\n");
    }

    private static String sign(PrivateKey key, String message) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(key);
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "----- "
                + Base64.getMimeEncoder(64, " ".getBytes(StandardCharsets.UTF_8)).encodeToString(encoded)
                + " -----END " + label + "-----";
    }

    /**
     * 按 URL 片段分派的假出网客户端：网页授权返回 openid，下单返回 prepay_id，
     * 查单返回预置响应队列。
     */
    public static final class FakeChannelHttpClient implements WechatHttpClient {

        private final List<Exchange> exchanges = new ArrayList<>();
        private final Deque<HttpTextResponse> oauthResponses = new ArrayDeque<>();
        private final Deque<HttpTextResponse> orderResponses = new ArrayDeque<>();
        private final Deque<HttpTextResponse> queryResponses = new ArrayDeque<>();

        public void reset() {
            exchanges.clear();
            oauthResponses.clear();
            orderResponses.clear();
            queryResponses.clear();
        }

        public void nextOpenId(String openid) {
            oauthResponses.add(new HttpTextResponse(
                    200, "{\"openid\":\"" + openid + "\",\"access_token\":\"tok\",\"expires_in\":7200}"));
        }

        public void nextOauthFailure() {
            oauthResponses.add(new HttpTextResponse(200, "{\"errcode\":40029,\"errmsg\":\"invalid code\"}"));
        }

        public void nextPrepayId(String prepayId) {
            orderResponses.add(new HttpTextResponse(200, "{\"prepay_id\":\"" + prepayId + "\"}"));
        }

        public void nextOrderFailure(int statusCode, String channelCode) {
            orderResponses.add(new HttpTextResponse(statusCode, "{\"code\":\"" + channelCode + "\"}"));
        }

        public void nextQueryResult(int statusCode, String body) {
            queryResponses.add(new HttpTextResponse(statusCode, body));
        }

        public List<Exchange> exchanges() {
            return List.copyOf(exchanges);
        }

        @Override
        public HttpTextResponse send(String httpMethod, String url, Map<String, String> headers, String body) {
            exchanges.add(new Exchange(httpMethod, url, Map.copyOf(headers), body));
            if (url.contains("/sns/oauth2/access_token")) {
                return take(oauthResponses, url);
            }
            if (url.contains("/v3/pay/transactions/jsapi")) {
                return take(orderResponses, url);
            }
            if (url.contains("/v3/pay/transactions/out-trade-no/")) {
                return take(queryResponses, url);
            }
            throw new IllegalStateException("未预期的渠道调用: " + url);
        }

        private static HttpTextResponse take(Deque<HttpTextResponse> queue, String url) {
            if (queue.isEmpty()) {
                throw new IllegalStateException("没有预置响应: " + url);
            }
            return queue.removeFirst();
        }

        public record Exchange(String method, String url, Map<String, String> headers, String body) {
        }
    }
}
