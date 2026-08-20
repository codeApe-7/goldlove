package com.love.archive.payment.application;

/**
 * 统一下单结果。
 *
 * @param channelReference 渠道侧的下单引用（微信为 prepay_id，易支付为平台订单号或空）
 * @param payParameters    前端调起支付所需的参数
 */
public record CreateOrderResult(String channelReference, PayParameters payParameters) {
}
