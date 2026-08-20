package com.love.archive.identity.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

/** 注册即 FREE；付费或兑换激活码升 VIP；累计付费达阈值自动升 SVIP。 */
public enum MembershipTier {
    FREE("FREE"),
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
