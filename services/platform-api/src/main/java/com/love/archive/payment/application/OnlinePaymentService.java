package com.love.archive.payment.application;

import com.love.archive.common.web.ApiException;
import com.love.archive.payment.config.OnlinePaymentProperties;
import com.love.archive.payment.domain.WechatOrderStatus;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.WechatPaymentOrderEntity;
import com.love.archive.wechatpay.application.CreateOrderCommand;
import com.love.archive.wechatpay.application.CreateOrderResult;
import com.love.archive.wechatpay.application.NotifyPayload;
import com.love.archive.wechatpay.application.PaymentChannel;
import com.love.archive.wechatpay.application.PaymentResult;
import com.love.archive.wechatpay.application.WechatOAuthGateway;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 线上支付编排：网页授权换 openid → 生成商户订单号 → 落订单 → 渠道下单，
 * 以及回调验签后的幂等结算。金额永远取服务端配置，绝不信任前端传入。
 */
@Service
@RequiredArgsConstructor
public class OnlinePaymentService {

    private static final int OUT_TRADE_NO_RANDOM_BYTES = 24;

    private final PaymentChannel paymentChannel;
    private final WechatOAuthGateway oauthGateway;
    private final CurrentAuthorizationDocumentPort authorizationDocumentPort;
    private final WechatPaymentOrderStore orderStore;
    private final OnlinePaymentProperties properties;
    private final SecureRandom secureRandom;

    /** 渠道配置与下单金额，供前端拉起网页授权。 */
    public OnlinePaymentSettingsView settings() {
        if (!paymentChannel.configured()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_CHANNEL_NOT_CONFIGURED", "支付渠道尚未配置");
        }
        return new OnlinePaymentSettingsView(
                oauthGateway.appId(),
                properties.getRegistrationAmountMinor(),
                properties.getOrderDescription());
    }

    public String authorizeUrl(String state) {
        return oauthGateway.authorizeUrl(state);
    }

    /**
     * 用公众号网页授权码换取 openid 并下单。
     *
     * @param authorizationDocumentVersion 用户付款前看到的授权书版本
     */
    public OnlineOrderView createOrder(String authorizationCode, String authorizationDocumentVersion) {
        String openid = oauthGateway.resolveOpenId(authorizationCode);
        long documentId = authorizationDocumentPort.requireActiveDocumentId(authorizationDocumentVersion);
        long amountMinor = properties.getRegistrationAmountMinor();
        String description = properties.getOrderDescription();
        String outTradeNo = generateOutTradeNo();

        orderStore.insertCreated(outTradeNo, openid, documentId, amountMinor, description);
        CreateOrderResult channelResult = paymentChannel.createOrder(
                new CreateOrderCommand(outTradeNo, description, amountMinor, openid));
        orderStore.attachPrepayId(outTradeNo, channelResult.prepayId());
        return new OnlineOrderView(
                outTradeNo, amountMinor, authorizationDocumentVersion, channelResult.payParameters());
    }

    /**
     * 处理微信支付回调：先验签解密，再幂等结算。
     *
     * @return 结算后的订单状态
     */
    public WechatOrderStatus handleNotification(NotifyPayload payload) {
        PaymentResult result = paymentChannel.verifyAndDecodeNotify(payload);
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
        WechatPaymentOrderEntity order = orderStore.findByOutTradeNo(outTradeNo)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "支付订单不存在"));
        if (order.getStatus() == WechatOrderStatus.CREATED) {
            Optional<PaymentResult> channelResult = paymentChannel.queryByOutTradeNo(outTradeNo);
            if (channelResult.isPresent() && channelResult.get().paid()) {
                orderStore.settle(channelResult.get());
                return reload(outTradeNo);
            }
        }
        return toStatusView(order);
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
}
