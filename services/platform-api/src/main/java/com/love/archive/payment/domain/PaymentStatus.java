package com.love.archive.payment.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum PaymentStatus {
    PAID("PAID"),
    REFUNDED("REFUNDED");

    @EnumValue
    private final String databaseValue;

    PaymentStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
