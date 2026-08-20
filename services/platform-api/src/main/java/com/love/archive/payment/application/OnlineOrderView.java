package com.love.archive.payment.application;

/**
 * 下单结果。amountMinor 来自服务端配置，前端只负责用 payParameters 调起支付。
 *
 * <p>{@code paid} 为真表示复用了该手机号此前已支付但未注册的订单，此时
 * {@code payParameters} 为 null，前端应直接去领注册令牌而不是再次调起支付。</p>
 */
public record OnlineOrderView(
        String outTradeNo,
        long amountMinor,
        String authorizationDocumentVersion,
        PayParameters payParameters,
        boolean paid) {
}
