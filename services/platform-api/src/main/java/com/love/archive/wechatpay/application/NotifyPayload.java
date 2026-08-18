package com.love.archive.wechatpay.application;

/**
 * 微信支付回调原文与验签头。
 *
 * @param serial    Wechatpay-Serial，平台公钥 ID 或平台证书序列号
 * @param timestamp Wechatpay-Timestamp
 * @param nonce     Wechatpay-Nonce
 * @param signature Wechatpay-Signature
 * @param body      未经任何加工的原始请求体
 */
public record NotifyPayload(
        String serial,
        String timestamp,
        String nonce,
        String signature,
        String body) {
}
