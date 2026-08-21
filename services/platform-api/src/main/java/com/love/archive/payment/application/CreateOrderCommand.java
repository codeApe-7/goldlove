package com.love.archive.payment.application;

import com.love.archive.payment.domain.PaymentChannelType;

/**
 * 统一下单命令。
 *
 * @param outTradeNo  商户订单号，全局唯一，作为幂等锚点
 * @param description 商品描述
 * @param amountMinor 下单金额（分），以后端订单为准
 * @param payer       支付者标识；当前渠道不需要，保留以便日后接入需要授权前置的渠道
 */
public record CreateOrderCommand(
        String outTradeNo,
        String description,
        long amountMinor,
        String payer) {
}
