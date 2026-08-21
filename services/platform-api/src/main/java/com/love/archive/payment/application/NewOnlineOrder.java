package com.love.archive.payment.application;

import com.love.archive.payment.domain.PaymentChannelType;

/**
 * 待落库的 VIP 升级订单。
 *
 * @param outTradeNo    商户订单号，全局唯一并作为幂等锚点
 * @param userAccountId 付款账号，下单时必须已登录
 * @param channel       支付渠道
 * @param amountMinor   下单金额（分），取服务端配置
 */
record NewOnlineOrder(
        String outTradeNo,
        long userAccountId,
        PaymentChannelType channel,
        long amountMinor) {
}
