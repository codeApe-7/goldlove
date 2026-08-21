package com.love.archive.payment.application;

/**
 * 下单结果。amountMinor 来自服务端配置，前端只负责用 payParameters 跳转收银台。
 */
public record OnlineOrderView(
        String outTradeNo,
        long amountMinor,
        PayParameters payParameters) {
}
