package com.love.archive.payment.application;

/**
 * 微信 JSAPI 调起支付（{@code wx.chooseWXPay} / {@code WeixinJSBridge}）所需的参数。
 * 这些字段可以下发前端；商户私钥、AppSecret、API v3 密钥不在其中。
 */
public record WechatJsapiPayParameters(
        String appId,
        String timeStamp,
        String nonceStr,
        String packageValue,
        String signType,
        String paySign) {
}
