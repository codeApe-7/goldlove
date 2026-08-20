package com.love.archive.payment.application;

/**
 * 下单结果。amountMinor 来自服务端配置，前端只负责用 payParameters 调起支付。
 */
public record OnlineOrderView(
        String outTradeNo,
        long amountMinor,
        String authorizationDocumentVersion,
        PayParameters payParameters) {
}
