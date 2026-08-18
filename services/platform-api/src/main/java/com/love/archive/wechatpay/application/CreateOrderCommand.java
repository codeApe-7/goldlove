package com.love.archive.wechatpay.application;

/**
 * @param outTradeNo  商户订单号，全局唯一，作为幂等锚点
 * @param description 商品描述
 * @param amountMinor 下单金额（分），以后端订单为准
 * @param openid      支付者 openid
 */
public record CreateOrderCommand(
        String outTradeNo,
        String description,
        long amountMinor,
        String openid) {
}
