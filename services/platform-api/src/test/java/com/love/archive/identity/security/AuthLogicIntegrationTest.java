package com.love.archive.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.stp.StpLogic;
import com.love.archive.testsupport.RedisIntegrationTest;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;

class AuthLogicIntegrationTest extends RedisIntegrationTest {

    @Autowired
    @Qualifier("guestStpLogic")
    private StpLogic guest;

    @Autowired
    @Qualifier("adminStpLogic")
    private StpLogic admin;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void guestAndAdminWithSameNumericIdHaveIsolatedRedisSessions() {
        assertThat(guest.getTokenName()).isEqualTo("archive-token-guest");
        assertThat(admin.getTokenName()).isEqualTo("archive-token-admin");

        String guestToken = guest.createLoginSession(42L);
        String adminToken = admin.createLoginSession(42L);

        assertThat(guestToken).isNotEqualTo(adminToken);
        assertThat(guest.getLoginIdByToken(guestToken)).hasToString("42");
        assertThat(admin.getLoginIdByToken(adminToken)).hasToString("42");
        assertThat(guest.getLoginIdByToken(adminToken)).isNull();
        assertThat(admin.getLoginIdByToken(guestToken)).isNull();

        Set<String> keys = redis.keys("*");
        assertThat(keys).anyMatch(key -> key.contains("guest"));
        assertThat(keys).anyMatch(key -> key.contains("admin"));

        guest.logoutByTokenValue(guestToken);

        assertThat(guest.getLoginIdByToken(guestToken)).isNull();
        assertThat(admin.getLoginIdByToken(adminToken)).hasToString("42");
    }
}
