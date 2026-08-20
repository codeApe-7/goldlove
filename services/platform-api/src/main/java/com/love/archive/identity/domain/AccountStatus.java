package com.love.archive.identity.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum AccountStatus {
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
