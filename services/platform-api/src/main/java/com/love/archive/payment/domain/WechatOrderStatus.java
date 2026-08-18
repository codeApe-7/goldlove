package com.love.archive.payment.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum WechatOrderStatus {
    CREATED("CREATED"),
    PAID("PAID"),
    CLOSED("CLOSED"),
    REFUNDED("REFUNDED");

    @EnumValue
    private final String databaseValue;

    WechatOrderStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
