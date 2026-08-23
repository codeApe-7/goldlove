package com.love.archive.identity.security;

import cn.dev33.satoken.stp.StpLogic;
import com.love.archive.common.security.AdminIdentity;
import com.love.archive.common.security.GuestAccountIdentity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public final class AuthLogics implements GuestAccountIdentity, AdminIdentity {

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

    @Override
    public long currentGuestAccountId() {
        return guest.getLoginIdAsLong();
    }

    public StpLogic admin() {
        return admin;
    }

    @Override
    public long currentAdminId() {
        return admin.getLoginIdAsLong();
    }
}
