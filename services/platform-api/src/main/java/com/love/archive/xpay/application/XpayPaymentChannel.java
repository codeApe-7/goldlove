package com.love.archive.xpay.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.love.archive.common.web.ApiException;
import com.love.archive.payment.application.CreateOrderCommand;
import com.love.archive.payment.application.CreateOrderResult;
import com.love.archive.payment.application.NotifyPayload;
import com.love.archive.payment.application.PayParameters;
import com.love.archive.payment.application.PaymentChannel;
import com.love.archive.payment.application.PaymentResult;
import com.love.archive.payment.domain.PaymentChannelType;
import com.love.archive.xpay.config.XpayProperties;
import com.love.archive.xpay.support.XpayCryptography;
import com.love.archive.xpay.support.XpayHttpClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 易支付（XPay V2）渠道实现：API 下单指定支付宝（method=jump 返回跳转链接）、
 * 查单、回调验签。无需 payer 授权前置。无凭据时不装配 {@link XpayCryptography}。
 */
@Service
public class XpayPaymentChannel implements PaymentChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(XpayPaymentChannel.class);
    private static final String SUBMIT_PATH = "/api/pay/submit";
    private static final String QUERY_PATH = "/api/pay/query";
    private static final String PAY_TYPE_ALIPAY = "alipay";

    private final ObjectProvider<XpayCryptography> cryptographyProvider;
    private final XpayHttpClient httpClient;
    private final XpayProperties properties;
    private final ObjectMapper objectMapper;

    public XpayPaymentChannel(
            ObjectProvider<XpayCryptography> cryptographyProvider,
            XpayHttpClient httpClient,
            XpayProperties properties,
            ObjectMapper objectMapper) {
        this.cryptographyProvider = cryptographyProvider;
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentChannelType kind() {
        return PaymentChannelType.XPAY_ALIPAY;
    }

    @Override
    public boolean configured() {
        return cryptographyProvider.getIfAvailable() != null;
    }

    @Override
    public boolean requiresPayerAuthorization() {
        return false;
    }

    @Override
    public String payerAuthorizationUrl(String state) {
        throw payerAuthorizationUnsupported();
    }

    @Override
    public String resolvePayer(String authorizationCode) {
        throw payerAuthorizationUnsupported();
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderCommand command) {
        XpayCryptography cryptography = requireCryptography();
        if (command.amountMinor() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT_INVALID", "下单金额必须大于零");
        }

        Map<String, String> params = new TreeMap<>();
        params.put("pid", properties.pid());
        params.put("type", PAY_TYPE_ALIPAY);
        params.put("out_trade_no", command.outTradeNo());
        params.put("notify_url", properties.notifyUrl());
        if (hasText(properties.returnUrl())) {
            params.put("return_url", properties.returnUrl());
        }
        params.put("name", truncateName(command.description()));
        params.put("money", toYuan(command.amountMinor()));
        params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
        params.put("sign_type", "RSA");
        params.put("sign", cryptography.sign(params));

        XpayHttpClient.HttpTextResponse response = httpClient.postForm(
                properties.resolvedBaseUrl() + SUBMIT_PATH, params);
        LOGGER.info("易支付下单响应, status={}, body={}", response.statusCode(), response.body());
        // 页面支付（submit）返回 302 重定向到收银台，跳转链接在 Location 头。
        if (response.statusCode() == 302) {
            String location = response.headers().get("location");
            if (!hasText(location)) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_RESPONSE_INVALID", "支付渠道响应缺少跳转链接");
            }
            return new CreateOrderResult(
                    null, new PayParameters(PaymentChannelType.XPAY_ALIPAY, resolveJumpUrl(location), null));
        }
        JsonNode payload = readJson(response.body());
        int code = payload.path("code").asInt(-1);
        if (response.statusCode() != 200 || code != 0) {
            LOGGER.warn("易支付下单失败, outTradeNo={}, status={}, code={}, msg={}",
                    command.outTradeNo(), response.statusCode(), code, text(payload, "msg"));
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_ORDER_FAILED", "支付渠道下单失败");
        }
        String tradeNo = text(payload, "trade_no");
        String payInfo = text(payload, "pay_info");
        if (!hasText(payInfo)) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_RESPONSE_INVALID", "支付渠道响应缺少跳转链接");
        }
        return new CreateOrderResult(
                tradeNo,
                new PayParameters(PaymentChannelType.XPAY_ALIPAY, payInfo, null));
    }

    @Override
    public Optional<PaymentResult> queryByOutTradeNo(String outTradeNo) {
        XpayCryptography cryptography = requireCryptography();
        Map<String, String> params = new TreeMap<>();
        params.put("pid", properties.pid());
        params.put("out_trade_no", outTradeNo);
        params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
        params.put("sign_type", "RSA");
        params.put("sign", cryptography.sign(params));

        XpayHttpClient.HttpTextResponse response = httpClient.postForm(
                properties.resolvedBaseUrl() + QUERY_PATH, params);
        JsonNode payload = readJson(response.body());
        int code = payload.path("code").asInt(-1);
        if (response.statusCode() != 200 || code != 0) {
            LOGGER.warn("易支付查单失败, outTradeNo={}, status={}, code={}",
                    outTradeNo, response.statusCode(), code);
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_QUERY_FAILED", "支付渠道查询失败");
        }
        return Optional.of(toPaymentResult(payload));
    }

    @Override
    public PaymentResult verifyAndDecodeNotify(NotifyPayload payload) {
        XpayCryptography cryptography = requireCryptography();
        Map<String, String> params = payload.params();
        if (params.isEmpty()) {
            throw signatureInvalid();
        }
        String sign = params.get("sign");
        if (!cryptography.verify(params, sign)) {
            throw signatureInvalid();
        }
        return toPaymentResult(toJsonNode(params));
    }

    private PaymentResult toPaymentResult(JsonNode node) {
        String outTradeNo = text(node, "out_trade_no");
        String tradeNo = text(node, "trade_no");
        String money = text(node, "money");
        boolean paid = "1".equals(text(node, "status"))
                || "TRADE_SUCCESS".equals(text(node, "trade_status"));
        long amountMinor = toMinor(money);
        return new PaymentResult(
                outTradeNo,
                hasText(tradeNo) ? tradeNo : text(node, "api_trade_no"),
                paid,
                amountMinor,
                amountMinor,
                text(node, "buyer"),
                paid ? OffsetDateTime.now() : null);
    }

    private JsonNode toJsonNode(Map<String, String> params) {
        try {
            return objectMapper.valueToTree(params);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_RESPONSE_INVALID", "支付回调无法解析");
        }
    }

    private XpayCryptography requireCryptography() {
        XpayCryptography cryptography = cryptographyProvider.getIfAvailable();
        if (cryptography == null) {
            throw notConfigured();
        }
        return cryptography;
    }

    /** 302 的 Location 可能是相对路径，拼上易支付站点 origin 得到完整跳转地址。 */
    private String resolveJumpUrl(String location) {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location;
        }
        URI base = URI.create(properties.resolvedBaseUrl());
        String origin = base.getScheme() + "://" + base.getHost();
        if (base.getPort() > 0) {
            origin += ":" + base.getPort();
        }
        return origin + location;
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (JacksonException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_RESPONSE_INVALID", "支付渠道响应无法解析");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private static String truncateName(String description) {
        String value = description == null || description.isBlank() ? "建档服务" : description.strip();
        return value.length() <= 127 ? value : value.substring(0, 127);
    }

    private static String toYuan(long amountMinor) {
        return BigDecimal.valueOf(amountMinor).movePointLeft(2).toPlainString();
    }

    private static long toMinor(String yuan) {
        if (yuan == null || yuan.isBlank()) {
            return 0L;
        }
        return new BigDecimal(yuan.strip())
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ApiException notConfigured() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_CHANNEL_NOT_CONFIGURED", "支付渠道尚未配置");
    }

    private static ApiException signatureInvalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "PAYMENT_NOTIFY_SIGNATURE_INVALID", "支付回调验签失败");
    }

    private static ApiException payerAuthorizationUnsupported() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "PAYMENT_CHANNEL_UNSUPPORTED", "该渠道不支持支付者授权");
    }
}
