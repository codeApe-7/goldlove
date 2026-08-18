package com.love.archive.payment.web;

/**
 * 下发前端的渠道设置。authorizeUrl 的回跳地址由服务端配置固定。
 */
public record OnlinePaymentSettingsResponse(
        String appId,
        long amountMinor,
        String orderDescription,
        String authorizeUrl,
        String state) {
}
