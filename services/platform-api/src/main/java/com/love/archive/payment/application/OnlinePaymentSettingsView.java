package com.love.archive.payment.application;

/**
 * 可以下发前端的渠道信息。仅含公众号 AppID 与服务端确定的下单金额，
 * 不含商户私钥、AppSecret 或 API v3 密钥。
 */
public record OnlinePaymentSettingsView(
        String appId,
        long registrationAmountMinor,
        String orderDescription) {
}
