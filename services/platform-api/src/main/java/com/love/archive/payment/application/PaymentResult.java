package com.love.archive.payment.application;

import java.time.OffsetDateTime;

/**
 * 渠道侧的支付结果，来自查单或回调解密。
 *
 * @param outTradeNo       商户订单号
 * @param transactionId    渠道交易号（微信交易号 / 易支付平台订单号）
 * @param paid             是否已支付成功
 * @param totalAmountMinor 订单金额（分）
 * @param paidAmountMinor  用户实付金额（分）
 * @param payer            支付者标识（可空）
 * @param successTime      支付成功时间，未支付时为空
 */
public record PaymentResult(
        String outTradeNo,
        String transactionId,
        boolean paid,
        long totalAmountMinor,
        long paidAmountMinor,
        String payer,
        OffsetDateTime successTime) {
}
