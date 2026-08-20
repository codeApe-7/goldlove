package com.love.archive.identity.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum ActivationCodeStatus {
    UNUSED("UNUSED"),
    USED("USED"),
    REVOKED("REVOKED");

    @EnumValue
    private final String databaseValue;

    ActivationCodeStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
