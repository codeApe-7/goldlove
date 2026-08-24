package com.love.archive.payment.application;

import com.love.archive.payment.domain.PaymentOrderStatus;
import java.time.OffsetDateTime;

/**
 * 订单状态查询结果。
 *
 * @param channelTradeNo    渠道侧订单号，下单成功即有；对账时用它去渠道后台找这笔单子
 * @param expiresAt         可支付截止时间，存量订单为空
 * @param membershipGranted 该笔付款是否已计入会员额度
 */
public record OnlineOrderStatusView(
        String outTradeNo,
        PaymentOrderStatus status,
        long amountMinor,
        boolean membershipGranted,
        String channelTradeNo,
        OffsetDateTime expiresAt) {
}
