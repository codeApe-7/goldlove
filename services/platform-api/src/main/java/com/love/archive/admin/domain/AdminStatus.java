package com.love.archive.admin.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum AdminStatus {
    ACTIVE("ACTIVE"),
    DISABLED("DISABLED");

    @EnumValue
    private final String databaseValue;

    AdminStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}

