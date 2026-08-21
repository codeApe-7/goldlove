package com.love.archive.payment.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum PaymentOrderStatus {
    CREATED("CREATED"),
    PAID("PAID"),
    CLOSED("CLOSED");

    @EnumValue
    private final String databaseValue;

    PaymentOrderStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
