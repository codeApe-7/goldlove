package com.love.archive.payment.application;

import com.love.archive.common.web.ApiException;
import com.love.archive.payment.config.OnlinePaymentProperties;
import com.love.archive.payment.domain.PaymentChannelType;
import com.love.archive.payment.domain.WechatOrderStatus;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.WechatPaymentOrderEntity;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 线上支付编排：按配置选择渠道，生成商户订单号 → 落订单 → 渠道下单，
 * 以及回调验签后的幂等结算。金额永远取服务端配置，绝不信任前端传入。
 */
@Service
@RequiredArgsConstructor
public class OnlinePaymentService {

    private static final int OUT_TRADE_NO_RANDOM_BYTES = 24;

    private final List<PaymentChannel> channels;
    private final CurrentAuthorizationDocumentPort authorizationDocumentPort;
    private final WechatPaymentOrderStore orderStore;
    private final OnlinePaymentProperties properties;
    private final SecureRandom secureRandom;

    /** 渠道类型与下单金额，供前端决定跳转授权或直接下单。 */
    public OnlinePaymentSettingsView settings() {
        PaymentChannel channel = activeChannel();
        return new OnlinePaymentSettingsView(
                channel.kind(),
                properties.getRegistrationAmountMinor(),
                properties.getOrderDescription());
    }

    public boolean requiresPayerAuthorization() {
        return activeChannel().requiresPayerAuthorization();
    }

    public String payerAuthorizationUrl(String state) {
        return activeChannel().payerAuthorizationUrl(state);
    }

    /**
     * 下单：需要前置授权的渠道先换 payer，否则 payer 为空。
     *
     * @param authorizationDocumentVersion 用户付款前看到的授权书版本
     */
    public OnlineOrderView createOrder(String authorizationCode, String authorizationDocumentVersion) {
        PaymentChannel channel = activeChannel();
        String payer = channel.requiresPayerAuthorization()
                ? channel.resolvePayer(authorizationCode)
                : null;
        long documentId = authorizationDocumentPort.requireActiveDocumentId(authorizationDocumentVersion);
        long amountMinor = properties.getRegistrationAmountMinor();
        String description = properties.getOrderDescription();
        String outTradeNo = generateOutTradeNo();

        orderStore.insertCreated(outTradeNo, payer, channel.kind(), documentId, amountMinor, description);
        CreateOrderResult channelResult = channel.createOrder(
                new CreateOrderCommand(outTradeNo, description, amountMinor, payer));
        orderStore.attachPrepayId(outTradeNo, channelResult.channelReference());
        return new OnlineOrderView(
                outTradeNo, amountMinor, authorizationDocumentVersion, channelResult.payParameters());
    }

    /**
     * 处理渠道支付回调：先验签解析，再幂等结算。
     *
     * @return 结算后的订单状态
     */
    public WechatOrderStatus handleNotification(PaymentChannelType channelType, NotifyPayload payload) {
        PaymentChannel channel = channelOf(channelType);
        PaymentResult result = channel.verifyAndDecodeNotify(payload);
        if (result.outTradeNo() == null || result.outTradeNo().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "PAYMENT_NOTIFY_SIGNATURE_INVALID", "支付回调验签失败");
        }
        return orderStore.settle(result).status();
    }

    /**
     * 查询订单状态。回调可能晚到或丢失，因此本地仍为 CREATED 时主动向渠道查单补偿。
     */
    public OnlineOrderStatusView status(String outTradeNo) {
        PaymentChannel channel = activeChannel();
        WechatPaymentOrderEntity order = orderStore.findByOutTradeNo(outTradeNo)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "支付订单不存在"));
        if (order.getStatus() == WechatOrderStatus.CREATED) {
            Optional<PaymentResult> channelResult = channel.queryByOutTradeNo(outTradeNo);
            if (channelResult.isPresent() && channelResult.get().paid()) {
                orderStore.settle(channelResult.get());
                return reload(outTradeNo);
            }
        }
        return toStatusView(order);
    }

    private PaymentChannel activeChannel() {
        List<PaymentChannel> configured = channels.stream()
                .filter(PaymentChannel::configured)
                .toList();
        if (configured.isEmpty()) {
            throw notConfigured();
        }
        String provider = properties.getProvider();
        if (provider != null && !provider.isBlank()) {
            return configured.stream()
                    .filter(channel -> channel.kind().name().equals(provider.strip()))
                    .findFirst()
                    .orElseThrow(OnlinePaymentService::notConfigured);
        }
        if (configured.size() > 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT_CHANNEL_AMBIGUOUS", "已配置多个支付渠道，请指定 provider");
        }
        return configured.get(0);
    }

    private PaymentChannel channelOf(PaymentChannelType channelType) {
        return channels.stream()
                .filter(channel -> channel.kind() == channelType && channel.configured())
                .findFirst()
                .orElseThrow(OnlinePaymentService::notConfigured);
    }

    private OnlineOrderStatusView reload(String outTradeNo) {
        return toStatusView(orderStore.findByOutTradeNo(outTradeNo)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "支付订单不存在")));
    }

    private OnlineOrderStatusView toStatusView(WechatPaymentOrderEntity order) {
        boolean registered = false;
        if (order.getPaymentRecordId() != null) {
            PaymentRecordEntity payment = orderStore.requirePaymentRecord(order.getPaymentRecordId());
            registered = Boolean.TRUE.equals(payment.getRegistered());
        }
        return new OnlineOrderStatusView(
                order.getOutTradeNo(), order.getStatus(), order.getAmountMinor(), registered);
    }

    private String generateOutTradeNo() {
        byte[] random = new byte[OUT_TRADE_NO_RANDOM_BYTES];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static ApiException notConfigured() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_CHANNEL_NOT_CONFIGURED", "支付渠道尚未配置");
    }
}
