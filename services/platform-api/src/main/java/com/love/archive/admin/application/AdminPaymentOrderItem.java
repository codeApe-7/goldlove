package com.love.archive.admin.application;

import java.time.OffsetDateTime;

/**
 * 台账里的一笔订单。
 *
 * @param channelTradeNo 渠道侧订单号，下单成功即有（未支付也有）；
 *                       对账时拿它去渠道后台找这笔单子，没有它就只能靠时间和金额瞎猜
 */
public record AdminPaymentOrderItem(
        long id,
        String outTradeNo,
        String phone,
        String channel,
        long amountMinor,
        String status,
        String channelTradeNo,
        OffsetDateTime paidAt,
        OffsetDateTime createdAt) {
}
