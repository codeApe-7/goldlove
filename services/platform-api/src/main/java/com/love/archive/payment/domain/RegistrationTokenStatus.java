package com.love.archive.payment.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum RegistrationTokenStatus {
    UNUSED("UNUSED"),
    USED("USED"),
    EXPIRED("EXPIRED");

    @EnumValue
    private final String databaseValue;

    RegistrationTokenStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
