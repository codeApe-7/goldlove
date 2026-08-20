package com.love.archive.admin.application;

import java.time.OffsetDateTime;

public record AdminPaymentOrderItem(
        long id,
        String outTradeNo,
        String phone,
        String channel,
        long amountMinor,
        String status,
        OffsetDateTime paidAt,
        OffsetDateTime createdAt) {
}
