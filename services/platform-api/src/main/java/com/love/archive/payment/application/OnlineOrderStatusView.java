package com.love.archive.payment.application;

import com.love.archive.payment.domain.PaymentOrderStatus;

/**
 * 订单状态查询结果。
 *
 * @param membershipGranted 该笔付款是否已计入会员额度
 */
public record OnlineOrderStatusView(
        String outTradeNo,
        PaymentOrderStatus status,
        long amountMinor,
        boolean membershipGranted) {
}
