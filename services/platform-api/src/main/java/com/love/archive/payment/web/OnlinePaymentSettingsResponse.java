package com.love.archive.payment.web;

import com.love.archive.payment.domain.PaymentChannelType;

/**
 * 下发前端的渠道设置。authorizeUrl 仅在需要 payer 授权的渠道（微信）非空；
 * 其回跳地址由服务端配置固定。
 */
public record OnlinePaymentSettingsResponse(
        PaymentChannelType channelType,
        long amountMinor,
        String orderDescription,
        String authorizeUrl,
        String state) {
}
