package com.love.archive.identity.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum MembershipTier {
    VIP("VIP"),
    SVIP("SVIP");

    @EnumValue
    private final String databaseValue;

    MembershipTier(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
