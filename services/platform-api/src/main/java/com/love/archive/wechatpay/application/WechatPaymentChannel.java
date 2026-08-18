package com.love.archive.wechatpay.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.love.archive.common.web.ApiException;
import com.love.archive.wechatpay.config.WechatPayProperties;
import com.love.archive.wechatpay.support.WechatHttpClient;
import com.love.archive.wechatpay.support.WechatPayCryptography;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 微信支付 API v3（JSAPI）渠道实现，同时承担公众号网页授权换取 openid。
 * 无凭据时不装配 {@link WechatPayCryptography}，所有出网方法返回
 * {@code PAYMENT_CHANNEL_NOT_CONFIGURED}，不影响应用启动。
 */
@Service
public class WechatPaymentChannel implements PaymentChannel, WechatOAuthGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(WechatPaymentChannel.class);
    private static final String JSAPI_ORDER_PATH = "/v3/pay/transactions/jsapi";
    private static final String QUERY_PATH_PREFIX = "/v3/pay/transactions/out-trade-no/";
    private static final String SIGN_TYPE = "RSA";
    private static final Duration NOTIFY_TIMESTAMP_TOLERANCE = Duration.ofMinutes(5);
    private static final int MAX_DESCRIPTION_LENGTH = 127;

    private final ObjectProvider<WechatPayCryptography> cryptographyProvider;
    private final WechatHttpClient httpClient;
    private final WechatPayProperties properties;
    private final ObjectMapper objectMapper;

    public WechatPaymentChannel(
            ObjectProvider<WechatPayCryptography> cryptographyProvider,
            WechatHttpClient httpClient,
            WechatPayProperties properties,
            ObjectMapper objectMapper) {
        this.cryptographyProvider = cryptographyProvider;
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean configured() {
        return cryptographyProvider.getIfAvailable() != null;
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderCommand command) {
        WechatPayCryptography cryptography = requireCryptography();
        requireText(command.outTradeNo(), "商户订单号");
        requireText(command.openid(), "支付者标识");
        if (command.amountMinor() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT_INVALID", "下单金额必须大于零");
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("appid", properties.appId());
        request.put("mchid", properties.merchantId());
        request.put("description", truncate(command.description()));
        request.put("out_trade_no", command.outTradeNo());
        request.put("notify_url", properties.notifyUrl());
        request.putObject("amount").put("total", command.amountMinor()).put("currency", "CNY");
        request.putObject("payer").put("openid", command.openid());

        String body = writeJson(request);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = cryptography.nonce();
        String authorization = cryptography.authorizationHeader(
                "POST", JSAPI_ORDER_PATH, body, timestamp, nonce);

        WechatHttpClient.HttpTextResponse response = httpClient.send(
                "POST",
                properties.resolvedApiBaseUrl() + JSAPI_ORDER_PATH,
                Map.of(
                        "Authorization", authorization,
                        "Content-Type", "application/json",
                        "Accept", "application/json",
                        "User-Agent", "marriage-archive-platform"),
                body);

        if (response.statusCode() != 200) {
            throw orderFailure(command.outTradeNo(), response);
        }
        JsonNode payload = readJson(response.body());
        String prepayId = text(payload, "prepay_id");
        if (prepayId == null) {
            throw orderFailure(command.outTradeNo(), response);
        }

        String paymentTimestamp = String.valueOf(Instant.now().getEpochSecond());
        String paymentNonce = cryptography.nonce();
        String paySign = cryptography.signJsapiPayment(
                properties.appId(), paymentTimestamp, paymentNonce, prepayId);
        return new CreateOrderResult(
                prepayId,
                new JsapiPayParameters(
                        properties.appId(),
                        paymentTimestamp,
                        paymentNonce,
                        "prepay_id=" + prepayId,
                        SIGN_TYPE,
                        paySign));
    }

    @Override
    public Optional<PaymentResult> queryByOutTradeNo(String outTradeNo) {
        WechatPayCryptography cryptography = requireCryptography();
        requireText(outTradeNo, "商户订单号");
        String canonicalUrl = QUERY_PATH_PREFIX + encodePathSegment(outTradeNo)
                + "?mchid=" + encodeQuery(properties.merchantId());
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = cryptography.nonce();
        String authorization = cryptography.authorizationHeader("GET", canonicalUrl, "", timestamp, nonce);

        WechatHttpClient.HttpTextResponse response = httpClient.send(
                "GET",
                properties.resolvedApiBaseUrl() + canonicalUrl,
                Map.of(
                        "Authorization", authorization,
                        "Accept", "application/json",
                        "User-Agent", "marriage-archive-platform"),
                null);

        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            LOGGER.warn("支付渠道查单失败, outTradeNo={}, status={}, channelCode={}",
                    outTradeNo, response.statusCode(), channelErrorCode(response.body()));
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_QUERY_FAILED", "支付渠道查询失败");
        }
        return Optional.of(toPaymentResult(readJson(response.body())));
    }

    @Override
    public PaymentResult verifyAndDecodeNotify(NotifyPayload payload) {
        WechatPayCryptography cryptography = requireCryptography();
        if (!matchesPlatformKey(cryptography, payload.serial())
                || !freshTimestamp(payload.timestamp())
                || !cryptography.verifyNotifySignature(
                        payload.timestamp(), payload.nonce(), payload.body(), payload.signature())) {
            throw signatureInvalid();
        }

        JsonNode notification = readJson(payload.body());
        JsonNode resource = notification.path("resource");
        if (resource.isMissingNode() || resource.isNull()) {
            throw signatureInvalid();
        }
        String decrypted;
        try {
            decrypted = cryptography.decryptResource(
                    text(resource, "associated_data"),
                    text(resource, "nonce"),
                    text(resource, "ciphertext"));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw signatureInvalid();
        }
        return toPaymentResult(readJson(decrypted));
    }

    @Override
    public String appId() {
        requireCryptography();
        return properties.appId();
    }

    @Override
    public String authorizeUrl(String state) {
        requireCryptography();
        String redirectUri = properties.oauthRedirectUri();
        if (redirectUri == null || redirectUri.isBlank()) {
            throw notConfigured();
        }
        return "https://open.weixin.qq.com/connect/oauth2/authorize"
                + "?appid=" + encodeQuery(properties.appId())
                + "&redirect_uri=" + encodeQuery(redirectUri)
                + "&response_type=code"
                + "&scope=snsapi_base"
                + "&state=" + encodeQuery(state == null ? "" : state)
                + "#wechat_redirect";
    }

    @Override
    public String resolveOpenId(String authorizationCode) {
        requireCryptography();
        requireText(authorizationCode, "网页授权码");
        String url = properties.resolvedOauthBaseUrl() + "/sns/oauth2/access_token"
                + "?appid=" + encodeQuery(properties.appId())
                + "&secret=" + encodeQuery(properties.appSecret())
                + "&code=" + encodeQuery(authorizationCode)
                + "&grant_type=authorization_code";

        WechatHttpClient.HttpTextResponse response = httpClient.send(
                "GET", url, Map.of("Accept", "application/json"), null);
        if (response.statusCode() != 200) {
            throw authorizationCodeInvalid();
        }
        JsonNode payload = readJson(response.body());
        String subject = text(payload, "openid");
        if (subject == null || subject.isBlank()) {
            LOGGER.warn("网页授权换取用户标识失败, channelCode={}", text(payload, "errcode"));
            throw authorizationCodeInvalid();
        }
        return subject;
    }

    private PaymentResult toPaymentResult(JsonNode transaction) {
        JsonNode amount = transaction.path("amount");
        long total = amount.path("total").asLong(0L);
        long payerTotal = amount.path("payer_total").asLong(total);
        return new PaymentResult(
                text(transaction, "out_trade_no"),
                text(transaction, "transaction_id"),
                TradeState.from(text(transaction, "trade_state")),
                total,
                payerTotal,
                text(transaction.path("payer"), "openid"),
                parseTime(text(transaction, "success_time")));
    }

    private boolean matchesPlatformKey(WechatPayCryptography cryptography, String serial) {
        return serial != null && serial.equals(cryptography.platformPublicKeyId());
    }

    private static boolean freshTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return false;
        }
        try {
            long seconds = Long.parseLong(timestamp.strip());
            long deltaSeconds = Math.abs(Instant.now().getEpochSecond() - seconds);
            return deltaSeconds <= NOTIFY_TIMESTAMP_TOLERANCE.toSeconds();
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static OffsetDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.strip());
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    private WechatPayCryptography requireCryptography() {
        WechatPayCryptography cryptography = cryptographyProvider.getIfAvailable();
        if (cryptography == null) {
            throw notConfigured();
        }
        return cryptography;
    }

    private ApiException orderFailure(String outTradeNo, WechatHttpClient.HttpTextResponse response) {
        String channelCode = channelErrorCode(response.body());
        LOGGER.warn("支付渠道下单失败, outTradeNo={}, status={}, channelCode={}",
                outTradeNo, response.statusCode(), channelCode);
        if ("ORDER_CLOSED".equals(channelCode) || "ORDERPAID".equals(channelCode)) {
            return new ApiException(
                    HttpStatus.CONFLICT, "PAYMENT_ORDER_ALREADY_PAID", "该订单已支付或已关闭");
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_ORDER_FAILED", "支付渠道下单失败");
    }

    private String channelErrorCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode payload = objectMapper.readTree(body);
            String code = text(payload, "code");
            return code != null ? code : text(payload, "errcode");
        } catch (com.fasterxml.jackson.core.JacksonException exception) {
            return null;
        }
    }

    private String writeJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (com.fasterxml.jackson.core.JacksonException exception) {
            throw new IllegalStateException("支付请求序列化失败", exception);
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (com.fasterxml.jackson.core.JacksonException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_RESPONSE_INVALID", "支付渠道响应无法解析");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String truncate(String description) {
        String value = description == null || description.isBlank() ? "建档服务" : description.strip();
        return value.length() <= MAX_DESCRIPTION_LENGTH
                ? value
                : value.substring(0, MAX_DESCRIPTION_LENGTH);
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePathSegment(String value) {
        return encodeQuery(value).replace("+", "%20");
    }

    private static void requireText(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "PAYMENT_REQUEST_INVALID", description + "不能为空");
        }
    }

    private static ApiException notConfigured() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_CHANNEL_NOT_CONFIGURED", "支付渠道尚未配置");
    }

    private static ApiException signatureInvalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "PAYMENT_NOTIFY_SIGNATURE_INVALID", "支付回调验签失败");
    }

    private static ApiException authorizationCodeInvalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "WECHAT_AUTHORIZATION_CODE_INVALID", "微信授权码无效或已过期");
    }
}
