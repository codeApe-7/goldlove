package com.love.archive.identity.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum IdentityProvider {
    WECHAT("WECHAT");

    @EnumValue
    private final String databaseValue;

    IdentityProvider(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}

