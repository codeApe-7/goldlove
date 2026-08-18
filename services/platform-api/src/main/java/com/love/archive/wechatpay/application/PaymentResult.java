package com.love.archive.wechatpay.application;

import java.time.OffsetDateTime;

/**
 * 渠道侧的支付结果，来自查单或回调解密。
 *
 * @param outTradeNo       商户订单号
 * @param transactionId    微信交易号
 * @param tradeState       交易状态
 * @param totalAmountMinor 订单金额（分）
 * @param paidAmountMinor  用户实付金额（分）
 * @param openid           支付者 openid
 * @param successTime      支付成功时间，未支付时为空
 */
public record PaymentResult(
        String outTradeNo,
        String transactionId,
        TradeState tradeState,
        long totalAmountMinor,
        long paidAmountMinor,
        String openid,
        OffsetDateTime successTime) {

    public boolean paid() {
        return tradeState == TradeState.SUCCESS;
    }
}
