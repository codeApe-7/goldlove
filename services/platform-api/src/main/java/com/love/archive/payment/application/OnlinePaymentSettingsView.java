package com.love.archive.payment.application;

import com.love.archive.payment.domain.PaymentChannelType;

/**
 * 可以下发前端的渠道信息。仅含渠道类型与服务端确定的下单金额，
 * 不含任何密钥；授权地址由 controller 按渠道能力另行生成。
 */
public record OnlinePaymentSettingsView(
        PaymentChannelType channelType,
        long amountMinor,
        String orderDescription) {
}
