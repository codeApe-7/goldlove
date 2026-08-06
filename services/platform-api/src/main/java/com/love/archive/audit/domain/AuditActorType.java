package com.love.archive.audit.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum AuditActorType {
    ADMIN("ADMIN"),
    GUEST("GUEST"),
    SYSTEM("SYSTEM");

    @EnumValue
    private final String databaseValue;

    AuditActorType(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}

