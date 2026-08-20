package com.love.archive.payment.application;

import com.love.archive.payment.domain.PaymentChannelType;

/**
 * 前端调起支付所需的参数。按 {@link #channelType} 取用不同字段：
 * 易支付（XPAY_ALIPAY）用 {@link #jumpUrl}，微信（WECHAT_JSAPI）用 {@link #wechatJsapi}。
 * 商户私钥、AppSecret 等密钥绝不包含其中。
 */
public record PayParameters(
        PaymentChannelType channelType,
        String jumpUrl,
        WechatJsapiPayParameters wechatJsapi) {
}
