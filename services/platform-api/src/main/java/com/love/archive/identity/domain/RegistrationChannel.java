package com.love.archive.identity.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum RegistrationChannel {
    ADMIN_MANUAL("ADMIN_MANUAL"),
    WECHAT_ONLINE("WECHAT_ONLINE"),
    ONLINE("ONLINE");

    @EnumValue
    private final String databaseValue;

    RegistrationChannel(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
