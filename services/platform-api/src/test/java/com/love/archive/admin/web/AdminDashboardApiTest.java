package com.love.archive.admin.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.testsupport.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@Import(AdminDashboardApiTest.FixedClockConfiguration.class)
class AdminDashboardApiTest extends ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private StringRedisTemplate redis;

    private long adminId;
    private Cookie adminCookie;

    @BeforeEach
    void prepareAdmin() throws Exception {
        resetDatabase();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        adminId = insertAdmin();
        adminCookie = login();
        seedStatsRows();
    }

    @Test
    void requiresAdminLogin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsStatCountersIncludingMembershipTiersAndTodayRevenue() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/stats").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAccounts").value(4))
                .andExpect(jsonPath("$.data.todayRegistrations").value(3))
                .andExpect(jsonPath("$.data.totalProfiles").value(4))
                .andExpect(jsonPath("$.data.completedProfiles").value(2))
                // 4 个账号默认 FREE，其中 1 个升 VIP、1 个升 SVIP。
                .andExpect(jsonPath("$.data.vipMembers").value(1))
                .andExpect(jsonPath("$.data.svipMembers").value(1))
                .andExpect(jsonPath("$.data.todayPaidAmountMinor").value(9900));
    }

    private void seedStatsRows() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO user_account (
                            phone, password_hash, status, created_at, updated_at, version
                        ) VALUES (?, 'x', 'ACTIVE', ?, ?, 0)
                        """)) {
            // 今日（Asia/Shanghai 2030-07-01，UTC [06-30 16:00, 07-01 16:00)）：3 条在边界内
            for (int i = 0; i < 3; i++) {
                statement.setString(1, "1380013800" + i);
                statement.setObject(2, OffsetDateTime.parse("2030-06-30T16:30:00Z"));
                statement.setObject(3, OffsetDateTime.parse("2030-06-30T16:30:00Z"));
                statement.addBatch();
            }
            statement.executeBatch();
            // 第 4 个账号在今日边界之外（UTC 06-30 15:00），计入累计但不计入今日注册
            statement.setString(1, "13800138003");
            statement.setObject(2, OffsetDateTime.parse("2030-06-30T14:30:00Z"));
            statement.setObject(3, OffsetDateTime.parse("2030-06-30T14:30:00Z"));
            statement.executeUpdate();
        }
        execute("""
                INSERT INTO guest_profile (profile_no, user_account_id, status, version)
                SELECT gen_random_uuid(), ua.id,
                       CASE WHEN ua.phone IN ('13800138000', '13800138001') THEN 'COMPLETED' ELSE 'DRAFT' END,
                       0
                FROM user_account ua
                """);
        // 今日一笔 ¥99 付款，账号升 VIP；另一个账号直接置 SVIP 模拟累计达标。
        execute("""
                INSERT INTO payment_record (
                    user_account_id, out_trade_no, transaction_id, channel, amount_minor,
                    status, membership_credit_minor, paid_at
                )
                SELECT ua.id, 'OTN-STATS-1', 'TXN-STATS-1', 'XPAY_ALIPAY', 9900,
                       'PAID', 9900, ?
                FROM user_account ua WHERE ua.phone = '13800138000'
                """, OffsetDateTime.parse("2030-07-01T02:00:00Z"));
        execute("""
                UPDATE user_account SET membership_tier = 'VIP', membership_credit_minor = 9900
                WHERE phone = '13800138000'
                """);
        execute("""
                UPDATE user_account SET membership_tier = 'SVIP', membership_credit_minor = 59900
                WHERE phone = '13800138001'
                """);
    }

    private static void execute(String sql, Object... args) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }

    private long insertAdmin() {
        char[] password = "dashboard-2026".toCharArray();
        String hash;
        try {
            hash = passwordHasher.hash(password);
        } finally {
            Arrays.fill(password, '\0');
        }
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("dashboard-admin");
        admin.setDisplayName("Dashboard Admin");
        admin.setPasswordHash(hash);
        admin.setStatus(AdminStatus.ACTIVE);
        OffsetDateTime now = OffsetDateTime.now();
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminMapper.insert(admin);
        return admin.getId();
    }

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dashboard-admin","password":"dashboard-2026"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("archive-token-admin");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedAdminDashboardClock() {
            return Clock.fixed(Instant.parse("2030-07-01T10:15:30Z"), ZoneOffset.UTC);
        }
    }
}
