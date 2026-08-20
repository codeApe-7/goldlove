package com.love.archive.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.payment.application.CreateOrderCommand;
import com.love.archive.payment.application.CreateOrderResult;
import com.love.archive.payment.application.NotifyPayload;
import com.love.archive.payment.application.PayParameters;
import com.love.archive.payment.application.PaymentChannel;
import com.love.archive.payment.application.PaymentResult;
import com.love.archive.payment.domain.PaymentChannelType;
import com.love.archive.payment.persistence.PaymentOrderEntity;
import com.love.archive.payment.persistence.PaymentOrderMapper;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.PaymentRecordMapper;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * VIP 升级支付：下单挂在当前登录账号上，结算后授予会员并累计额度。
 * 渠道用一个假实现顶替，验签与 HTTP 往返由 XpayPaymentChannelTest 单独覆盖。
 */
@Import(VipUpgradePaymentApiTest.StubChannelConfiguration.class)
@TestPropertySource(properties = {
        "app.payment.online.vip-upgrade-amount-minor=9900",
        "app.membership.svip-threshold-minor=19800"})
class VipUpgradePaymentApiTest extends ApiIntegrationTest {

    private static final String PHONE = "13800138000";
    private static final String PASSWORD = "Guest-vip-pay-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountMapper accountMapper;
    @Autowired private PaymentOrderMapper orderMapper;
    @Autowired private PaymentRecordMapper paymentRecordMapper;
    @Autowired private StubPaymentChannel stubChannel;
    @Autowired private StringRedisTemplate redis;

    private String guestToken;

    @BeforeEach
    void prepare() throws Exception {
        resetDatabase();
        resetRateLimits(redis);
        stubChannel.reset();
        guestToken = registerGuest(mockMvc, PHONE, PASSWORD);
    }

    @Test
    void requiresLoginForSettingsOrderAndStatus() throws Exception {
        mockMvc.perform(get("/api/v1/guest/vip-payments/settings"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/guest/vip-payments/orders"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/guest/vip-payments/orders/anything"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exposesServerSideAmountOnly() throws Exception {
        mockMvc.perform(get("/api/v1/guest/vip-payments/settings")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelType").value("XPAY_ALIPAY"))
                .andExpect(jsonPath("$.data.amountMinor").value(9900));
    }

    @Test
    void ordersAreBoundToTheLoggedInAccountAtTheConfiguredAmount() throws Exception {
        String outTradeNo = createOrder();

        PaymentOrderEntity order = orderMapper.selectOne(
                Wrappers.<PaymentOrderEntity>lambdaQuery()
                        .eq(PaymentOrderEntity::getOutTradeNo, outTradeNo));
        assertThat(order.getAmountMinor()).isEqualTo(9900L);
        assertThat(order.getUserAccountId()).isEqualTo(accountId());
        assertThat(order.getStatus().name()).isEqualTo("CREATED");
    }

    @Test
    void notificationSettlesOnceAndGrantsVip() throws Exception {
        String outTradeNo = createOrder();

        notifyPaid(outTradeNo, 9900L, "TXN-1");
        notifyPaid(outTradeNo, 9900L, "TXN-1");

        assertThat(paymentRecordMapper.selectCount(Wrappers.<PaymentRecordEntity>lambdaQuery()))
                .isOne();
        assertThat(reloadAccount().getMembershipTier().name()).isEqualTo("VIP");
        assertThat(reloadAccount().getMembershipCreditMinor()).isEqualTo(9900L);
    }

    @Test
    void cumulativePaymentsReachTheSvipThreshold() throws Exception {
        notifyPaid(createOrder(), 9900L, "TXN-A");
        assertThat(reloadAccount().getMembershipTier().name()).isEqualTo("VIP");

        notifyPaid(createOrder(), 9900L, "TXN-B");

        assertThat(reloadAccount().getMembershipTier().name()).isEqualTo("SVIP");
        assertThat(reloadAccount().getMembershipCreditMinor()).isEqualTo(19_800L);
    }

    @Test
    void rejectsUnverifiedNotificationsAndAmountMismatches() throws Exception {
        String outTradeNo = createOrder();

        // 没有可验签的回执：按微信/易支付惯例返回 401，不给渠道重试的余地。
        mockMvc.perform(post("/api/v1/public/payment-notifications/xpay")
                        .param("out_trade_no", outTradeNo))
                .andExpect(status().isUnauthorized());

        // 验签通过但金额与服务端订单不符：500 让渠道重试，绝不结算。
        stubChannel.nextNotify(new PaymentResult(
                outTradeNo, "TXN-CHEAP", true, 100L, 100L, null, OffsetDateTime.now()));
        mockMvc.perform(post("/api/v1/public/payment-notifications/xpay")
                        .param("out_trade_no", outTradeNo))
                .andExpect(status().isInternalServerError());

        assertThat(paymentRecordMapper.selectCount(Wrappers.<PaymentRecordEntity>lambdaQuery()))
                .isZero();
        assertThat(reloadAccount().getMembershipTier().name()).isEqualTo("FREE");
    }

    @Test
    void statusQueryCompensatesWhenTheNotificationNeverArrives() throws Exception {
        String outTradeNo = createOrder();
        stubChannel.nextQuery(new PaymentResult(
                outTradeNo, "TXN-LATE", true, 9900L, 9900L, null, OffsetDateTime.now()));

        mockMvc.perform(get("/api/v1/guest/vip-payments/orders/" + outTradeNo)
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.membershipGranted").value(true));

        assertThat(reloadAccount().getMembershipTier().name()).isEqualTo("VIP");
    }

    @Test
    void refusesToReadSomeoneElsesOrder() throws Exception {
        String outTradeNo = createOrder();
        String otherToken = registerGuest(mockMvc, "13900139000", PASSWORD);

        mockMvc.perform(get("/api/v1/guest/vip-payments/orders/" + outTradeNo)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_ORDER_NOT_FOUND"));
    }

    private String createOrder() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/guest/vip-payments/orders")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payParameters.jumpUrl").isNotEmpty())
                .andReturn();
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .path("data").path("outTradeNo").asText();
    }

    private void notifyPaid(String outTradeNo, long amountMinor, String transactionId)
            throws Exception {
        stubChannel.nextNotify(new PaymentResult(
                outTradeNo, transactionId, true, amountMinor, amountMinor, null, OffsetDateTime.now()));
        mockMvc.perform(post("/api/v1/public/payment-notifications/xpay")
                        .param("out_trade_no", outTradeNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    private long accountId() {
        return reloadAccount().getId();
    }

    private UserAccountEntity reloadAccount() {
        return accountMapper.selectOne(Wrappers.<UserAccountEntity>lambdaQuery()
                .eq(UserAccountEntity::getPhone, PHONE));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubChannelConfiguration {

        @Bean
        StubPaymentChannel stubPaymentChannel() {
            return new StubPaymentChannel();
        }
    }

    /** 假渠道：只回放测试预置的结果，不做签名与网络往返。 */
    static class StubPaymentChannel implements PaymentChannel {

        private final AtomicReference<PaymentResult> notifyResult = new AtomicReference<>();
        private final AtomicReference<PaymentResult> queryResult = new AtomicReference<>();

        void reset() {
            notifyResult.set(null);
            queryResult.set(null);
        }

        void nextNotify(PaymentResult result) {
            notifyResult.set(result);
        }

        void nextQuery(PaymentResult result) {
            queryResult.set(result);
        }

        @Override
        public PaymentChannelType kind() {
            return PaymentChannelType.XPAY_ALIPAY;
        }

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public CreateOrderResult createOrder(CreateOrderCommand command) {
            return new CreateOrderResult(
                    "STUB-" + command.outTradeNo(),
                    new PayParameters(
                            PaymentChannelType.XPAY_ALIPAY,
                            "https://cashier.example.test/pay?order=" + command.outTradeNo()));
        }

        @Override
        public Optional<PaymentResult> queryByOutTradeNo(String outTradeNo) {
            return Optional.ofNullable(queryResult.get());
        }

        @Override
        public PaymentResult verifyAndDecodeNotify(NotifyPayload payload) {
            PaymentResult result = notifyResult.get();
            if (result == null) {
                throw new com.love.archive.common.web.ApiException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "PAYMENT_NOTIFY_SIGNATURE_INVALID",
                        "支付回调验签失败");
            }
            return result;
        }
    }
}
