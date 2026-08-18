package com.love.archive.wechatpay.application;

/**
 * 前端 {@code wx.chooseWXPay} / {@code WeixinJSBridge} 调起支付所需的参数。
 * 这些字段可以下发前端；商户私钥、AppSecret、API v3 密钥不在其中。
 */
public record JsapiPayParameters(
        String appId,
        String timeStamp,
        String nonceStr,
        String packageValue,
        String signType,
        String paySign) {
}
