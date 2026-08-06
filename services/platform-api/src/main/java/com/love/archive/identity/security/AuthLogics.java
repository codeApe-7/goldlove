package com.love.archive.identity.security;

import cn.dev33.satoken.stp.StpLogic;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public final class AuthLogics {

    private final StpLogic guest;
    private final StpLogic admin;

    public AuthLogics(
            @Qualifier("guestStpLogic") StpLogic guest,
            @Qualifier("adminStpLogic") StpLogic admin) {
        this.guest = guest;
        this.admin = admin;
    }

    public StpLogic guest() {
        return guest;
    }

    public StpLogic admin() {
        return admin;
    }
}
