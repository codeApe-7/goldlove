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
        mockMvc.perform(get("/api/v1/guest/vip-payments/orders"))
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

        PaymentOrderEntity order = findOrder(outTradeNo);
        assertThat(order.getAmountMinor()).isEqualTo(9900L);
        assertThat(order.getUserAccountId()).isEqualTo(accountId());
        assertThat(order.getStatus().name()).isEqualTo("CREATED");
    }

    /**
     * 渠道侧订单号在下单当场就要落库。它原本被丢掉了：{@code CreateOrderResult} 带着它，
     * 却没有任何地方读——于是一笔没付成的订单在库里没有任何能拿去渠道后台对账的编号。
     */
    @Test
    void theChannelTradeNoIsRecordedWhenTheOrderIsCreated() throws Exception {
        String outTradeNo = createOrder();

        assertThat(findOrder(outTradeNo).getChannelTradeNo()).isEqualTo("STUB-" + outTradeNo);
        mockMvc.perform(get("/api/v1/guest/vip-payments/orders/" + outTradeNo)
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelTradeNo").value("STUB-" + outTradeNo));
    }

    /**
     * 下单拿到的是带平台标记的订单号，不是一串 Base64。
     *
     * <p>这里走的是完整装配（真的 {@code OutTradeNoGenerator} bean），确保它真被接上了——
     * 形状本身由 {@code OutTradeNoGeneratorTest} 逐条钉住。</p>
     */
    @Test
    void newOrderNumbersCarryThePlatformPrefix() throws Exception {
        String outTradeNo = createOrder();

        assertThat(outTradeNo)
                .startsWith("GOLD-")
                .matches("GOLD-\\d{8}-\\d{6}-[23456789ABCDEFGHJKMNPQRSTVWXYZ]{6}");
        assertThat(findOrder(outTradeNo)).isNotNull();
    }

    /**
     * 下单即写截止时间，前端与关单逻辑都靠它判断「还能不能继续付」。
     *
     * <p>默认 5 分钟，对齐易支付收银台自己的超时。这个数字被钉在这里是有意的：
     * 配得比渠道长，界面上就会说「还能付」而点过去是一个已经死掉的收银台。</p>
     */
    @Test
    void newOrdersExpireInFiveMinutesToMatchTheChannelCashier() throws Exception {
        OffsetDateTime before = OffsetDateTime.now();

        OffsetDateTime expiresAt = findOrder(createOrder()).getExpiresAt();

        assertThat(expiresAt).isNotNull();
        assertThat(expiresAt)
                .isAfter(before.plusMinutes(4))
                .isBefore(before.plusMinutes(6));
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
    void rejectsUnverifiedNotificationsAndUnderpayments() throws Exception {
        String outTradeNo = createOrder();

        // 没有可验签的回执：按微信/易支付惯例返回 401，不给渠道重试的余地。
        mockMvc.perform(post("/api/v1/public/payment-notifications/xpay")
                        .param("out_trade_no", outTradeNo))
                .andExpect(status().isUnauthorized());

        // 验签通过但**少付**：500 让渠道重试，绝不结算。这是唯一真会亏钱的方向。
        stubChannel.nextNotify(new PaymentResult(
                outTradeNo, "TXN-CHEAP", true, 100L, 100L, null, OffsetDateTime.now()));
        mockMvc.perform(post("/api/v1/public/payment-notifications/xpay")
                        .param("out_trade_no", outTradeNo))
                .andExpect(status().isInternalServerError());

        assertThat(paymentRecordMapper.selectCount(Wrappers.<PaymentRecordEntity>lambdaQuery()))
                .isZero();
        assertThat(reloadAccount().getMembershipTier().name()).isEqualTo("FREE");
    }

    /**
     * 多付照常结算，并按**实付**计入会员额度。
     *
     * <p>这不是纵容，是易支付的正常行为：它会为了区分同额订单把金额往上加分。
     * 线上实测两笔都发 {@code money=0.01}，网关记成了 0.01 与 0.02。原来这里要求金额
     * <b>严格相等</b>，于是一笔真实付款被判成 {@code PAYMENT_AMOUNT_MISMATCH}，
     * 回调端点又把它映射成 500，网关无限重试——用户付了钱，订单永远停在待支付。</p>
     */
    @Test
    void settlesWhenTheChannelChargedMoreThanTheOrderAmount() throws Exception {
        String outTradeNo = createOrder();

        notifyPaid(outTradeNo, 9901L, "TXN-BUMPED");

        assertThat(findOrder(outTradeNo).getStatus().name()).isEqualTo("PAID");
        assertThat(reloadAccount().getMembershipTier().name()).isEqualTo("VIP");
        // 额度记的是真金白银，网关加的那一分也算进去。
        assertThat(reloadAccount().getMembershipCreditMinor()).isEqualTo(9901L);
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

    /**
     * 订单列表是「把订单找回来」的唯一途径。
     *
     * <p>原来订单号只活在前端的会话存储里，换个标签页或重新登录就丢，而查单接口要求
     * 调用方**已经知道订单号**——两下一凑，一笔没付成的订单就永久失联了。</p>
     */
    @Test
    void listsMyOrdersNewestFirst() throws Exception {
        String first = createOrder();
        String second = createOrder();

        mockMvc.perform(get("/api/v1/guest/vip-payments/orders")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].outTradeNo").value(second))
                .andExpect(jsonPath("$.data[1].outTradeNo").value(first))
                .andExpect(jsonPath("$.data[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data[0].amountMinor").value(9900))
                .andExpect(jsonPath("$.data[0].channelTradeNo").value("STUB-" + second))
                .andExpect(jsonPath("$.data[0].expiresAt").isNotEmpty());
    }

    /** 别人的订单绝不出现在我的列表里。 */
    @Test
    void theOrderListNeverLeaksAnotherAccountsOrders() throws Exception {
        String mine = createOrder();
        String otherToken = registerGuest(mockMvc, "13900139000", PASSWORD);

        mockMvc.perform(get("/api/v1/guest/vip-payments/orders")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/guest/vip-payments/orders")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].outTradeNo").value(mine));
    }

    /**
     * 过期未付的订单转 CLOSED，界面才分得清「还能付」和「已经死了，重新下一笔」。
     * 在此之前 CLOSED 只存在于枚举和 CHECK 里，全仓库没有一处写它，订单永远停在 CREATED。
     */
    @Test
    void expiredOrdersAreClosedWhenLookedAt() throws Exception {
        String outTradeNo = createOrder();
        expire(outTradeNo);

        mockMvc.perform(get("/api/v1/guest/vip-payments/orders")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("CLOSED"));
        assertThat(findOrder(outTradeNo).getStatus().name()).isEqualTo("CLOSED");
    }

    /**
     * 关单之后才付成也必须认账。
     *
     * <p>过期判定用的是我们自己的时钟，网关的失效窗口未知。如果结算只接受 CREATED，
     * 那么「我们提前关单 + 用户最后一刻付款」就会变成用户付了钱、系统拒收：
     * 回调撞上 {@code PAYMENT_ORDER_STATE_CONFLICT} → 500 → 网关无限重试 → 永远不到账。</p>
     */
    @Test
    void aLatePaymentStillSettlesAClosedOrder() throws Exception {
        String outTradeNo = createOrder();
        expire(outTradeNo);
        mockMvc.perform(get("/api/v1/guest/vip-payments/orders")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk());
        assertThat(findOrder(outTradeNo).getStatus().name()).isEqualTo("CLOSED");

        notifyPaid(outTradeNo, 9900L, "TXN-LATE-CLOSED");

        assertThat(findOrder(outTradeNo).getStatus().name()).isEqualTo("PAID");
        assertThat(reloadAccount().getMembershipTier().name()).isEqualTo("VIP");
    }

    /**
     * 渠道查不到这笔单子（线上实测 {@code code=1 / 没有找到订单信息}）时，
     * 查单接口要给出本地状态，而不是把 502「支付渠道查询失败」摔给用户。
     */
    @Test
    void statusFallsBackToTheLocalStateWhenTheChannelLostTheOrder() throws Exception {
        String outTradeNo = createOrder();
        expire(outTradeNo);
        stubChannel.nextQuery(null);

        mockMvc.perform(get("/api/v1/guest/vip-payments/orders/" + outTradeNo)
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.membershipGranted").value(false));
    }

    /** 把订单的截止时间挪到过去，模拟「放了一晚没付」。 */
    private void expire(String outTradeNo) {
        PaymentOrderEntity order = findOrder(outTradeNo);
        order.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        orderMapper.updateById(order);
    }

    private PaymentOrderEntity findOrder(String outTradeNo) {
        return orderMapper.selectOne(Wrappers.<PaymentOrderEntity>lambdaQuery()
                .eq(PaymentOrderEntity::getOutTradeNo, outTradeNo));
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
