package com.love.archive.payment.application;

import com.love.archive.payment.domain.WechatOrderStatus;

/**
 * 订单状态查询结果。
 *
 * @param registered 该订单是否已用于完成注册
 */
public record OnlineOrderStatusView(
        String outTradeNo,
        WechatOrderStatus status,
        long amountMinor,
        boolean registered) {
}
