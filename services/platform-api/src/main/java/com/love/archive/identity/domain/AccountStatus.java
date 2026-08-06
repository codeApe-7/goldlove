package com.love.archive.identity.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum AccountStatus {
    PAID_PENDING_ACTIVATION("PAID_PENDING_ACTIVATION"),
    ACTIVE("ACTIVE"),
    SUSPENDED("SUSPENDED"),
    CLOSED("CLOSED");

    @EnumValue
    private final String databaseValue;

    AccountStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
