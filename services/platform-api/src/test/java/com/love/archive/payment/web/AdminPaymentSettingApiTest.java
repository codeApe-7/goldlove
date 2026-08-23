package com.love.archive.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.payment.persistence.PaymentOrderEntity;
import com.love.archive.payment.persistence.PaymentOrderMapper;
import com.love.archive.testsupport.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 后台改支付金额。库里那一行覆盖配置，改完立刻对新订单生效。
 *
 * <p>渠道复用 {@link VipUpgradePaymentApiTest} 的假实现：这里要验证的是金额从哪儿来，
 * 不是签名与网络往返。</p>
 */
@Import(VipUpgradePaymentApiTest.StubChannelConfiguration.class)
@TestPropertySource(properties = "app.payment.online.vip-upgrade-amount-minor=9900")
class AdminPaymentSettingApiTest extends ApiIntegrationTest {

    private static final String TRUSTED_ORIGIN = "https://h5.example.test";
    private static final String PHONE = "13800138000";
    private static final String PASSWORD = "Guest-payment-setting-2026";
    private static final String ADMIN_USERNAME = "payment-setting-admin";
    private static final String ADMIN_PASSWORD = "payment-setting-admin-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private PaymentOrderMapper orderMapper;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private StringRedisTemplate redis;

    private Cookie adminCookie;

    @BeforeEach
    void prepare() throws Exception {
        resetDatabase();
        resetRateLimits(redis);
        insertAdmin();
        adminCookie = adminLogin();
    }

    @Test
    void requiresAdminLogin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payment-settings"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/admin/payment-settings")
                        .header("Origin", TRUSTED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vipUpgradeAmountMinor\":12800}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fallsBackToConfigurationUntilSomeoneSetsIt() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payment-settings").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vipUpgradeAmountMinor").value(9900))
                .andExpect(jsonPath("$.data.configuredAmountMinor").value(9900))
                .andExpect(jsonPath("$.data.managedInAdmin").value(false))
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist())
                // 上下限随视图下发，前端不必再抄一份这两个数字。
                .andExpect(jsonPath("$.data.minAmountMinor").value(1))
                .andExpect(jsonPath("$.data.maxAmountMinor").value(10_000_000));
    }

    @Test
    void savedAmountOverridesConfigurationForNewGuestOrders() throws Exception {
        updateAmount(12_800L);

        mockMvc.perform(get("/api/v1/admin/payment-settings").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vipUpgradeAmountMinor").value(12_800))
                // 配置值原样保留，界面上要说清「现在用的不是它」。
                .andExpect(jsonPath("$.data.configuredAmountMinor").value(9900))
                .andExpect(jsonPath("$.data.managedInAdmin").value(true))
                .andExpect(jsonPath("$.data.updatedAt").exists());

        String guestToken = registerGuest(mockMvc, PHONE, PASSWORD);
        mockMvc.perform(get("/api/v1/guest/vip-payments/settings")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountMinor").value(12_800));

        assertThat(amountOf(createOrder(guestToken))).isEqualTo(12_800L);
    }

    @Test
    void inFlightOrdersKeepTheAmountTheyWereCreatedWith() throws Exception {
        String guestToken = registerGuest(mockMvc, PHONE, PASSWORD);
        String outTradeNo = createOrder(guestToken);

        updateAmount(29_900L);

        // 改价不追溯：这笔订单已经把金额写进 payment_order，回调按订单金额核对。
        assertThat(amountOf(outTradeNo)).isEqualTo(9900L);
        assertThat(amountOf(createOrder(guestToken))).isEqualTo(29_900L);
    }

    @Test
    void rejectsAmountsOutsideTheAllowedRange() throws Exception {
        expectRejected("{\"vipUpgradeAmountMinor\":0}", "PAYMENT_AMOUNT_INVALID");
        expectRejected("{\"vipUpgradeAmountMinor\":-1}", "PAYMENT_AMOUNT_INVALID");
        expectRejected("{\"vipUpgradeAmountMinor\":10000001}", "PAYMENT_AMOUNT_INVALID");
        expectRejected("{}", "VALIDATION_FAILED");

        // 一次都没写进去，金额还是配置值。
        mockMvc.perform(get("/api/v1/admin/payment-settings").cookie(adminCookie))
                .andExpect(jsonPath("$.data.managedInAdmin").value(false));
    }

    @Test
    void reportsTheAllowedRangeInTheErrorMessage() throws Exception {
        mockMvc.perform(putAmount("{\"vipUpgradeAmountMinor\":10000001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("支付金额需在 ¥0.01 与 ¥100000.00 之间"));
    }

    @Test
    void writesOneAuditEntryPerRealChange() throws Exception {
        updateAmount(12_800L);
        updateAmount(12_800L);

        assertThat(auditCount()).isEqualTo(1);
        assertThat(auditMetadata())
                .contains("\"fromAmountMinor\":9900")
                .contains("\"fromSource\":\"CONFIG\"")
                .contains("\"toAmountMinor\":12800");

        updateAmount(19_900L);
        assertThat(auditCount()).isEqualTo(2);
    }

    @Test
    void rejectsWritesWithoutATrustedOrigin() throws Exception {
        mockMvc.perform(put("/api/v1/admin/payment-settings")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vipUpgradeAmountMinor\":12800}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_ORIGIN_REJECTED"));
    }

    private void updateAmount(long amountMinor) throws Exception {
        mockMvc.perform(putAmount("{\"vipUpgradeAmountMinor\":" + amountMinor + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vipUpgradeAmountMinor").value(amountMinor));
    }

    private void expectRejected(String body, String code) throws Exception {
        mockMvc.perform(putAmount(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder putAmount(
            String body) {
        return put("/api/v1/admin/payment-settings")
                .cookie(adminCookie)
                .header("Origin", TRUSTED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String createOrder(String guestToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/guest/vip-payments/orders")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andReturn();
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .path("data").path("outTradeNo").asText();
    }

    private long amountOf(String outTradeNo) {
        return orderMapper.selectOne(Wrappers.<PaymentOrderEntity>lambdaQuery()
                        .eq(PaymentOrderEntity::getOutTradeNo, outTradeNo))
                .getAmountMinor();
    }

    private long auditCount() {
        return Long.parseLong(queryString(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'PAYMENT_AMOUNT_UPDATED'"));
    }

    private String auditMetadata() {
        return queryString("""
                SELECT metadata FROM audit_log
                WHERE action = 'PAYMENT_AMOUNT_UPDATED' ORDER BY id LIMIT 1
                """);
    }

    private String queryString(String sql) {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : "";
        } catch (SQLException exception) {
            throw new IllegalStateException("读取审计日志失败", exception);
        }
    }

    private void insertAdmin() {
        char[] password = ADMIN_PASSWORD.toCharArray();
        String hash;
        try {
            hash = passwordHasher.hash(password);
        } finally {
            Arrays.fill(password, '\0');
        }
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername(ADMIN_USERNAME);
        admin.setDisplayName("Payment Setting Admin");
        admin.setPasswordHash(hash);
        admin.setStatus(AdminStatus.ACTIVE);
        OffsetDateTime now = OffsetDateTime.now();
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminMapper.insert(admin);
    }

    private Cookie adminLogin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .header("Origin", TRUSTED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("archive-token-admin");
    }
}
