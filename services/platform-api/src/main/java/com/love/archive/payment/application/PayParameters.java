package com.love.archive.payment.application;

import com.love.archive.payment.domain.PaymentChannelType;

/**
 * 前端跳转收银台所需的参数。易支付（XPAY_ALIPAY）用 {@link #jumpUrl}。
 * 商户私钥等密钥绝不包含其中。
 */
public record PayParameters(
        PaymentChannelType channelType,
        String jumpUrl) {
}
