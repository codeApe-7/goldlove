package com.love.archive.payment.application;

/**
 * 统一下单命令。
 *
 * @param outTradeNo  商户订单号，全局唯一，作为幂等锚点
 * @param description 商品描述
 * @param amountMinor 下单金额（分），以后端订单为准
 * @param payer       支付者标识（微信 openid 等）；无需授权前置的渠道为 null
 */
public record CreateOrderCommand(
        String outTradeNo,
        String description,
        long amountMinor,
        String payer) {
}
