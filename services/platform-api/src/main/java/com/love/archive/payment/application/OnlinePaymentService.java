package com.love.archive.payment.application;

import com.love.archive.common.web.ApiException;
import com.love.archive.payment.config.OnlinePaymentProperties;
import com.love.archive.payment.domain.PaymentChannelType;
import com.love.archive.payment.domain.PaymentOrderStatus;
import com.love.archive.payment.persistence.PaymentOrderEntity;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.PaymentRecordMapper;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * VIP 升级支付编排：生成商户订单号 → 落订单 → 渠道下单，以及回调验签后的幂等结算。
 * 金额永远取服务端的当前设置（后台值优先，其次配置），绝不信任前端传入；
 * 付款人一定是已登录账号。
 */
@Service
@RequiredArgsConstructor
public class OnlinePaymentService {

    private static final int OUT_TRADE_NO_RANDOM_BYTES = 24;
    /** 「我的订单」最多回多少条。会员订单本就零星，够看全历史，也不必分页。 */
    private static final int MAX_LISTED_ORDERS = 50;

    private final List<PaymentChannel> channels;
    private final PaymentOrderStore orderStore;
    private final PaymentRecordMapper paymentRecordMapper;
    private final MembershipGrantPort membershipGrantPort;
    private final OnlinePaymentProperties properties;
    private final PaymentSettingService paymentSettingService;
    private final SecureRandom secureRandom;

    /** 渠道类型与升级金额，供前端展示。 */
    public OnlinePaymentSettingsView settings() {
        PaymentChannel channel = activeChannel();
        return new OnlinePaymentSettingsView(
                channel.kind(),
                paymentSettingService.vipUpgradeAmountMinor(),
                properties.getOrderDescription());
    }

    /** 为当前登录账号创建一笔 VIP 升级订单并向渠道下单。 */
    public OnlineOrderView createOrder(long accountId) {
        PaymentChannel channel = activeChannel();
        long amountMinor = paymentSettingService.vipUpgradeAmountMinor();
        String description = properties.getOrderDescription();
        String outTradeNo = generateOutTradeNo();

        orderStore.insertCreated(new NewOnlineOrder(
                outTradeNo, accountId, channel.kind(), amountMinor, expiryFromNow()));
        CreateOrderResult channelResult = channel.createOrder(
                new CreateOrderCommand(outTradeNo, description, amountMinor, null));
        // 渠道单号一拿到就落库。它原本被丢在这里：CreateOrderResult 带着它，
        // 却没有任何地方读——于是一笔没付成的订单在库里没有任何能拿去渠道后台对账的编号。
        orderStore.recordChannelTradeNo(outTradeNo, channelResult.channelReference());
        return new OnlineOrderView(outTradeNo, amountMinor, channelResult.payParameters());
    }

    /** 本人的订单列表。返回前先把已过期仍未支付的订单关掉，界面才分得清能不能继续付。 */
    public List<OnlineOrderListItem> listOrders(long accountId) {
        orderStore.closeExpired(accountId);
        return orderStore.listByAccount(accountId, MAX_LISTED_ORDERS).stream()
                .map(order -> new OnlineOrderListItem(
                        order.getOutTradeNo(),
                        order.getStatus(),
                        order.getAmountMinor(),
                        order.getChannelTradeNo(),
                        order.getCreatedAt(),
                        order.getPaidAt(),
                        order.getExpiresAt()))
                .toList();
    }

    /**
     * 处理渠道支付回调：先验签解析，再幂等结算并授予会员。
     */
    public PaymentOrderStatus handleNotification(
            PaymentChannelType channelType, NotifyPayload payload) {
        PaymentChannel channel = channelOf(channelType);
        PaymentResult result = channel.verifyAndDecodeNotify(payload);
        if (result.outTradeNo() == null || result.outTradeNo().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "PAYMENT_NOTIFY_SIGNATURE_INVALID", "支付回调验签失败");
        }
        return settleAndGrant(result).status();
    }

    /**
     * 查询订单状态。回调可能晚到或丢失，因此本地还不是 PAID 时主动向渠道查单补偿。
     * 只允许查询本人的订单。
     *
     * <p>已经 CLOSED 的订单也照样查一次：关单用的是我们自己的时钟，
     * 万一用户在最后一刻付成了，这里得能把它捞回来。</p>
     */
    public OnlineOrderStatusView status(long accountId, String outTradeNo) {
        PaymentOrderEntity order = requireOwnOrder(accountId, outTradeNo);
        if (order.getStatus() == PaymentOrderStatus.PAID) {
            return toStatusView(order);
        }
        Optional<PaymentResult> channelResult = activeChannel().queryByOutTradeNo(outTradeNo);
        if (channelResult.isPresent()) {
            // 未支付的查单响应里也带 trade_no，顺手补上——这往往是我们唯一能拿到它的时机。
            orderStore.recordChannelTradeNo(outTradeNo, channelResult.get().transactionId());
            if (channelResult.get().paid()) {
                settleAndGrant(channelResult.get());
                return toStatusView(requireOwnOrder(accountId, outTradeNo));
            }
        }
        // 渠道说没付（或已经查不到这笔单子）：本地过期的就关掉，界面才能说「重新下单」
        // 而不是让用户对着一笔永远不会变的「待支付」反复点查询。
        if (orderStore.closeExpired(accountId) > 0) {
            return toStatusView(requireOwnOrder(accountId, outTradeNo));
        }
        return toStatusView(order);
    }

    /**
     * 结算并授予会员。授予放在结算事务之外：结算已幂等落库，
     * 而 {@code grantPaidMembership} 自身也按付款记录幂等，重放安全。
     */
    private PaymentOrderStore.SettlementOutcome settleAndGrant(PaymentResult result) {
        PaymentOrderStore.SettlementOutcome outcome = orderStore.settle(result);
        if (outcome.status() == PaymentOrderStatus.PAID
                && outcome.userAccountId() != null
                && outcome.paymentRecordId() != null) {
            membershipGrantPort.grantPaidMembership(
                    outcome.userAccountId(), outcome.paymentRecordId(), outcome.amountMinor());
        }
        return outcome;
    }

    private PaymentOrderEntity requireOwnOrder(long accountId, String outTradeNo) {
        PaymentOrderEntity order = orderStore.findByOutTradeNo(outTradeNo)
                .orElseThrow(() -> orderNotFound());
        // 订单号是随机的，但仍不能让别人的订单被任意账号读到。
        if (order.getUserAccountId() == null || order.getUserAccountId() != accountId) {
            throw orderNotFound();
        }
        return order;
    }

    private PaymentChannel activeChannel() {
        List<PaymentChannel> configured = channels.stream()
                .filter(PaymentChannel::configured)
                .toList();
        if (configured.isEmpty()) {
            throw notConfigured();
        }
        String provider = properties.getProvider();
        if (provider != null && !provider.isBlank()) {
            return configured.stream()
                    .filter(channel -> channel.kind().name().equals(provider.strip()))
                    .findFirst()
                    .orElseThrow(OnlinePaymentService::notConfigured);
        }
        if (configured.size() > 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PAYMENT_CHANNEL_AMBIGUOUS",
                    "已配置多个支付渠道，请指定 provider");
        }
        return configured.get(0);
    }

    private PaymentChannel channelOf(PaymentChannelType channelType) {
        return channels.stream()
                .filter(channel -> channel.kind() == channelType && channel.configured())
                .findFirst()
                .orElseThrow(OnlinePaymentService::notConfigured);
    }

    private OnlineOrderStatusView toStatusView(PaymentOrderEntity order) {
        boolean granted = false;
        if (order.getPaymentRecordId() != null) {
            PaymentRecordEntity payment = paymentRecordMapper.selectById(order.getPaymentRecordId());
            granted = payment != null
                    && payment.getMembershipCreditMinor() != null
                    && payment.getMembershipCreditMinor() > 0;
        }
        return new OnlineOrderStatusView(
                order.getOutTradeNo(), order.getStatus(), order.getAmountMinor(), granted,
                order.getChannelTradeNo(), order.getExpiresAt());
    }

    /** 配置成非正数即视为不过期——免得一个手滑的 0 让每笔新订单当场作废。 */
    private OffsetDateTime expiryFromNow() {
        long minutes = properties.getOrderExpiryMinutes();
        return minutes <= 0 ? null : OffsetDateTime.now().plusMinutes(minutes);
    }

    private String generateOutTradeNo() {
        byte[] random = new byte[OUT_TRADE_NO_RANDOM_BYTES];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static ApiException orderNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "支付订单不存在");
    }

    private static ApiException notConfigured() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_CHANNEL_NOT_CONFIGURED", "支付渠道尚未配置");
    }
}
