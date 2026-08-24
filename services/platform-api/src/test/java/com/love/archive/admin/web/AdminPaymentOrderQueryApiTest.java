package com.love.archive.admin.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

class AdminPaymentOrderQueryApiTest extends ApiIntegrationTest {

    private static final String PHONE = "13800138000";
    private static final String OTHER_PHONE = "13900139000";
    private static final String PASSWORD = "Guest-order-query-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private StringRedisTemplate redis;

    private Cookie adminCookie;

    @BeforeEach
    void prepare() throws Exception {
        resetDatabase();
        resetRateLimits(redis);
        insertAdmin();
        adminCookie = adminLogin();
        registerGuest(mockMvc, PHONE, PASSWORD);
        registerGuest(mockMvc, OTHER_PHONE, PASSWORD);
    }

    @Test
    void requiresAdminLogin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payment-orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsOrdersWithPayerPhoneAndStatus() throws Exception {
        insertOrder("OTN-PAID-1", PHONE, 9900L, "PAID");
        insertOrder("OTN-CREATED-1", OTHER_PHONE, 9900L, "CREATED");

        mockMvc.perform(get("/api/v1/admin/payment-orders").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[*].phone")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(PHONE, OTHER_PHONE)));
    }

    @Test
    void filtersByStatusAndPhone() throws Exception {
        insertOrder("OTN-PAID-1", PHONE, 9900L, "PAID");
        insertOrder("OTN-CREATED-1", OTHER_PHONE, 9900L, "CREATED");

        mockMvc.perform(get("/api/v1/admin/payment-orders")
                        .cookie(adminCookie)
                        .param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].outTradeNo").value("OTN-PAID-1"))
                .andExpect(jsonPath("$.data.items[0].amountMinor").value(9900))
                .andExpect(jsonPath("$.data.items[0].channel").value("XPAY_ALIPAY"));

        mockMvc.perform(get("/api/v1/admin/payment-orders")
                        .cookie(adminCookie)
                        .param("phone", "139"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].phone").value(OTHER_PHONE));
    }

    /**
     * 台账要带上渠道侧订单号，未支付的订单也要带。
     *
     * <p>这一列是对账的唯一抓手：线上真的遇到过一笔订单在渠道那边查不到，
     * 而库里当时没有任何能拿去渠道后台查的编号，只能靠时间和金额瞎猜。
     * 未支付时 `transaction_id` 还是空的，所以不能靠它。</p>
     */
    @Test
    void carriesTheChannelTradeNoForReconciliation() throws Exception {
        insertOrder("OTN-RECON-1", PHONE, 9900L, "CREATED", "20260824000620000000");
        insertOrder("OTN-NORECON-1", OTHER_PHONE, 9900L, "CREATED");

        mockMvc.perform(get("/api/v1/admin/payment-orders")
                        .cookie(adminCookie)
                        .param("phone", "138"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].outTradeNo").value("OTN-RECON-1"))
                .andExpect(jsonPath("$.data.items[0].channelTradeNo")
                        .value("20260824000620000000"));

        // 没有渠道单号的订单回 null，而不是漏掉这个字段——前端靠它决定显示占位符。
        mockMvc.perform(get("/api/v1/admin/payment-orders")
                        .cookie(adminCookie)
                        .param("phone", "139"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].channelTradeNo").isEmpty());
    }

    @Test
    void paginates() throws Exception {
        for (int i = 0; i < 5; i++) {
            insertOrder("OTN-PAGE-" + i, PHONE, 100L * (i + 1), "CREATED");
        }

        mockMvc.perform(get("/api/v1/admin/payment-orders")
                        .cookie(adminCookie)
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    private void insertOrder(String outTradeNo, String phone, long amountMinor, String status)
            throws SQLException {
        insertOrder(outTradeNo, phone, amountMinor, status, null);
    }

    private void insertOrder(
            String outTradeNo, String phone, long amountMinor, String status, String channelTradeNo)
            throws SQLException {
        Long paymentRecordId = null;
        if ("PAID".equals(status)) {
            paymentRecordId = insertPaymentRecord(outTradeNo, phone, amountMinor);
        }
        try (Connection owner = ownerConnection();
                PreparedStatement insert = owner.prepareStatement("""
                        INSERT INTO payment_order (
                            out_trade_no, user_account_id, channel, amount_minor, status,
                            transaction_id, paid_at, payment_record_id, channel_trade_no)
                        SELECT ?, ua.id, 'XPAY_ALIPAY', ?, ?, ?, ?, ?, ?
                        FROM user_account ua WHERE ua.phone = ?
                        """)) {
            insert.setString(1, outTradeNo);
            insert.setLong(2, amountMinor);
            insert.setString(3, status);
            insert.setString(4, paymentRecordId == null ? null : "TXN-" + outTradeNo);
            insert.setObject(5, paymentRecordId == null ? null : OffsetDateTime.now());
            insert.setObject(6, paymentRecordId);
            insert.setString(7, channelTradeNo);
            insert.setString(8, phone);
            insert.executeUpdate();
        }
    }

    private long insertPaymentRecord(String outTradeNo, String phone, long amountMinor)
            throws SQLException {
        try (Connection owner = ownerConnection();
                PreparedStatement insert = owner.prepareStatement("""
                        INSERT INTO payment_record (
                            user_account_id, out_trade_no, transaction_id, channel,
                            amount_minor, status, membership_credit_minor, paid_at)
                        SELECT ua.id, ?, ?, 'XPAY_ALIPAY', ?, 'PAID', ?, ?
                        FROM user_account ua WHERE ua.phone = ?
                        RETURNING id
                        """)) {
            insert.setString(1, outTradeNo);
            insert.setString(2, "TXN-" + outTradeNo);
            insert.setLong(3, amountMinor);
            insert.setLong(4, amountMinor);
            insert.setObject(5, OffsetDateTime.now());
            insert.setString(6, phone);
            try (var rows = insert.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void insertAdmin() {
        char[] password = "order-query-admin-2026".toCharArray();
        String hash;
        try {
            hash = passwordHasher.hash(password);
        } finally {
            Arrays.fill(password, '\0');
        }
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("order-query-admin");
        admin.setDisplayName("Order Query Admin");
        admin.setPasswordHash(hash);
        admin.setStatus(AdminStatus.ACTIVE);
        OffsetDateTime now = OffsetDateTime.now();
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminMapper.insert(admin);
    }

    private Cookie adminLogin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"order-query-admin","password":"order-query-admin-2026"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("archive-token-admin");
    }
}
