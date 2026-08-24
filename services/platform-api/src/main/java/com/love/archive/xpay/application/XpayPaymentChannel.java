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
        // 带上 outTradeNo 与 money。原来只记 status 与 body，而页面支付的 body 恒为 "Found"，
        // 于是一行日志既认不出是哪笔订单，也看不出我们究竟报了多少钱——
        // 排查「网关把 0.01 记成 0.02」时就是卡在这里。
        LOGGER.info("易支付下单响应, outTradeNo={}, money={}, status={}, body={}",
                command.outTradeNo(), params.get("money"), response.statusCode(), response.body());
        // 页面支付（submit）返回 302 重定向到收银台，跳转链接在 Location 头。
        if (response.statusCode() == 302) {
            String location = response.headers().get("location");
            if (!hasText(location)) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_RESPONSE_INVALID", "支付渠道响应缺少跳转链接");
            }
            return new CreateOrderResult(
                    channelTradeNoFrom(location),
                    new PayParameters(PaymentChannelType.XPAY_ALIPAY, resolveJumpUrl(location)));
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
                new PayParameters(PaymentChannelType.XPAY_ALIPAY, payInfo));
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
        // 响应体必须记下来。原来只记 status 与 code，一旦查单失败就完全看不出网关到底说了什么——
        // 线上真的遇到过 code=1 且无从判断它是「查询成功但未支付」还是「查询失败」，
        // 而下单路径（上面）本来就打了 body，两边口径不该不一致。
        LOGGER.info("易支付查单响应, outTradeNo={}, status={}, body={}",
                outTradeNo, response.statusCode(), response.body());
        JsonNode payload = readJson(response.body());
        int code = payload.path("code").asInt(-1);
        if (response.statusCode() != 200 || code != 0) {
            // 业务层面查不到结果 → 空，而不是抛异常。
            //
            // 「没有找到订单信息」（code=1）是这里最常见的回答：订单在网关那边过期后就查不到了。
            // 原来这种情况抛 502，于是用户点开一笔早已失效的订单，看到的是「支付渠道查询失败」，
            // 而真实答案是「这笔单子已经没了，重新下一笔吧」——本地的过期状态才知道这件事。
            //
            // 安全性不受影响：结算只在 paid()==true 时发生，查不到永远不会把订单推成已支付。
            // 传输失败与响应体无法解析仍然抛（在 postForm / readJson 里）。
            LOGGER.warn("易支付查单未返回结果, outTradeNo={}, status={}, code={}, msg={}",
                    outTradeNo, response.statusCode(), code, failureMessage(payload));
            return Optional.empty();
        }
        return Optional.of(toPaymentResult(queryDetail(payload)));
    }

    /**
     * 网关有两种错误壳：业务响应用 {@code msg}（{@code {"code":1,"msg":"没有找到订单信息"}}），
     * 网关自身拒绝时用 {@code message}（{@code {"code":502,"message":"必填sign"}}）。
     * 只读一个就会把另一种记成 null，白丢一条本来就在手里的线索。
     */
    private static String failureMessage(JsonNode payload) {
        String msg = text(payload, "msg");
        return hasText(msg) ? msg : text(payload, "message");
    }

    /**
     * 从收银台跳转地址里取出渠道侧订单号：{@code /pay/20260823225910918724} 的末段就是它。
     *
     * <p>页面支付的 302 不带响应体，这是下单当场唯一能拿到渠道单号的地方。存下来才有对账的抓手——
     * 没付成的订单在库里原本连一个能拿去渠道后台查的编号都没有。</p>
     */
    private static String channelTradeNoFrom(String location) {
        String path = location;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        String candidate = path.substring(path.lastIndexOf('/') + 1);
        // 只认纯数字的末段，避免把 /pay、/cashier 这类路径片段当成单号存进去。
        return candidate.length() >= 6 && candidate.chars().allMatch(Character::isDigit)
                ? candidate
                : null;
    }

    /**
     * 查单的业务字段可能包在 {@code data} 里，也可能平铺在根节点。
     *
     * <p>{@code XPAY-API.md} 的「查询订单」示例是包在 {@code data} 里的，
     * 而**线上实测网关回的是平铺形状**：
     * {@code {"code":0,"msg":"success","trade_no":"...","out_trade_no":"...","money":"0.01","status":0}}。
     * 两种都得认——照文档只读 {@code data} 会在生产上全取不到，
     * 照实测只读根节点又会在网关改回文档形状时全线失效。</p>
     *
     * <p>取错层级的后果不是报错而是静默错判：字段全取不到 → 解析成「未支付、订单号为空」，
     * <b>补偿查单这条安全网等于不存在</b>，回调丢了就再也补不回来。</p>
     */
    private static JsonNode queryDetail(JsonNode payload) {
        JsonNode data = payload.path("data");
        return data.isObject() && data.has("out_trade_no") ? data : payload;
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
}
