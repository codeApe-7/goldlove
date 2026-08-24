package com.love.archive.payment.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.web.ApiException;
import com.love.archive.payment.domain.PaymentOrderStatus;
import com.love.archive.payment.domain.PaymentStatus;
import com.love.archive.payment.persistence.PaymentOrderEntity;
import com.love.archive.payment.persistence.PaymentOrderMapper;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.PaymentRecordMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VIP 升级订单落库。事务只包住数据库操作，渠道出网调用留在
 * {@link OnlinePaymentService}，避免长时间占用连接池。
 */
@Service
@RequiredArgsConstructor
class PaymentOrderStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentOrderStore.class);
    private static final Pattern OUT_TRADE_NO = Pattern.compile("[A-Za-z0-9_-]{6,64}");

    private final PaymentOrderMapper orderMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final AuditTrail auditTrail;

    @Transactional
    void insertCreated(NewOnlineOrder command) {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOrderEntity order = new PaymentOrderEntity();
        order.setOutTradeNo(command.outTradeNo());
        order.setUserAccountId(command.userAccountId());
        order.setChannel(command.channel());
        order.setAmountMinor(command.amountMinor());
        order.setStatus(PaymentOrderStatus.CREATED);
        order.setExpiresAt(command.expiresAt());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);
    }

    /**
     * 记录渠道侧订单号。只在本地还没有时写，绝不覆盖——
     * 同一笔订单在渠道那边只会有一个单号，若两次拿到的不一样，那是异常而不是更新，
     * 覆盖只会把先前那个（很可能是对的）擦掉，让对账更难。
     */
    @Transactional
    void recordChannelTradeNo(String outTradeNo, String channelTradeNo) {
        if (!hasText(channelTradeNo) || !plausibleOutTradeNo(outTradeNo)) {
            return;
        }
        orderMapper.update(Wrappers.<PaymentOrderEntity>lambdaUpdate()
                .eq(PaymentOrderEntity::getOutTradeNo, outTradeNo)
                .isNull(PaymentOrderEntity::getChannelTradeNo)
                .set(PaymentOrderEntity::getChannelTradeNo, channelTradeNo)
                .set(PaymentOrderEntity::getUpdatedAt, OffsetDateTime.now()));
    }

    /**
     * 把该账号下已过期仍为 CREATED 的订单关掉，返回关掉的条数。
     *
     * <p>一条有界的 UPDATE，只碰调用者自己的行，幂等。刻意不做全表定时扫描：
     * 关单纯粹是为了让「还能付」和「已经死了」在界面上分得开，
     * 而会看到这个区别的时刻就是用户来看订单的时刻。</p>
     */
    @Transactional
    int closeExpired(long accountId) {
        return orderMapper.update(Wrappers.<PaymentOrderEntity>lambdaUpdate()
                .eq(PaymentOrderEntity::getUserAccountId, accountId)
                .eq(PaymentOrderEntity::getStatus, PaymentOrderStatus.CREATED)
                .isNotNull(PaymentOrderEntity::getExpiresAt)
                .lt(PaymentOrderEntity::getExpiresAt, OffsetDateTime.now())
                .set(PaymentOrderEntity::getStatus, PaymentOrderStatus.CLOSED)
                .set(PaymentOrderEntity::getUpdatedAt, OffsetDateTime.now()));
    }

    @Transactional(readOnly = true)
    List<PaymentOrderEntity> listByAccount(long accountId, int limit) {
        return orderMapper.selectList(Wrappers.<PaymentOrderEntity>lambdaQuery()
                .eq(PaymentOrderEntity::getUserAccountId, accountId)
                .orderByDesc(PaymentOrderEntity::getCreatedAt)
                .orderByDesc(PaymentOrderEntity::getId)
                .last("LIMIT " + limit));
    }

    /**
     * 幂等结算：首次收到支付成功时写付款记录并推进订单；重复回调直接返回已有结果。
     */
    @Transactional
    SettlementOutcome settle(PaymentResult result) {
        PaymentOrderEntity order = lockByOutTradeNo(result.outTradeNo());
        if (order.getStatus() == PaymentOrderStatus.PAID) {
            return new SettlementOutcome(
                    order.getStatus(), order.getUserAccountId(), order.getPaymentRecordId(),
                    order.getAmountMinor(), false);
        }
        if (!result.paid()) {
            return new SettlementOutcome(
                    order.getStatus(), order.getUserAccountId(), null, order.getAmountMinor(), false);
        }
        long settledAmountMinor = requireSufficientAmount(result, order);
        if (result.transactionId() == null || result.transactionId().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_RESPONSE_INVALID", "支付渠道响应缺少交易号");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime paidAt = result.successTime() == null ? now : result.successTime();

        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setUserAccountId(order.getUserAccountId());
        payment.setOutTradeNo(order.getOutTradeNo());
        payment.setTransactionId(result.transactionId());
        payment.setChannel(order.getChannel());
        // 记实付而不是下单金额：会员额度累计的是真金白银，网关加了分就该算进去。
        payment.setAmountMinor(settledAmountMinor);
        payment.setCurrency("CNY");
        payment.setStatus(PaymentStatus.PAID);
        payment.setMembershipCreditMinor(0L);
        payment.setPaidAt(paidAt);
        payment.setCreatedAt(now);
        paymentRecordMapper.insert(payment);

        // CLOSED 也允许推进到 PAID。我们的过期判定用的是自己的时钟，网关的失效窗口未知，
        // 真在我们关单之后付成了，钱照样得认——否则就成了「用户付了钱、系统拒收」。
        int updated = orderMapper.update(Wrappers.<PaymentOrderEntity>lambdaUpdate()
                .eq(PaymentOrderEntity::getId, order.getId())
                .in(PaymentOrderEntity::getStatus,
                        PaymentOrderStatus.CREATED, PaymentOrderStatus.CLOSED)
                .set(PaymentOrderEntity::getStatus, PaymentOrderStatus.PAID)
                .set(PaymentOrderEntity::getTransactionId, result.transactionId())
                .set(PaymentOrderEntity::getPaymentRecordId, payment.getId())
                .set(PaymentOrderEntity::getPaidAt, paidAt)
                .set(PaymentOrderEntity::getUpdatedAt, now));
        if (updated != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PAYMENT_ORDER_STATE_CONFLICT", "订单状态已变化，请重试");
        }

        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.SYSTEM,
                null,
                "VIP_PAYMENT_SETTLED",
                "PAYMENT_RECORD",
                payment.getId(),
                null,
                "{\"outTradeNo\":\"" + order.getOutTradeNo() + "\",\"orderAmountMinor\":"
                        + order.getAmountMinor() + ",\"paidAmountMinor\":" + settledAmountMinor + "}",
                now));
        return new SettlementOutcome(
                PaymentOrderStatus.PAID, order.getUserAccountId(), payment.getId(),
                settledAmountMinor, true);
    }

    /**
     * 实付少于应付才拒；多付照常结算。
     *
     * <p>原来这里要求**严格相等**，而易支付会为了区分同额订单把金额往上加分
     * （实测：两笔都发 money=0.01，网关记成 0.01 与 0.02），于是一笔真实付款会被判成
     * {@code PAYMENT_AMOUNT_MISMATCH}，回调端点又把它映射成 500，网关无限重试——
     * <b>用户付了钱，订单永远停在待支付</b>。这个等号挡的不是攻击，是网关的正常行为。</p>
     *
     * <p>少付必须继续拒：那是唯一真会亏钱的方向。金额本身来自平台公钥验签过的回调
     * 或查单响应，不是前端能摆布的值。</p>
     */
    private static long requireSufficientAmount(PaymentResult result, PaymentOrderEntity order) {
        long paid = result.totalAmountMinor();
        long ordered = order.getAmountMinor();
        if (paid < ordered) {
            LOGGER.warn("支付金额少于订单金额，拒绝结算, outTradeNo={}, ordered={}, paid={}",
                    order.getOutTradeNo(), ordered, paid);
            throw new ApiException(
                    HttpStatus.CONFLICT, "PAYMENT_AMOUNT_MISMATCH", "支付金额少于订单金额");
        }
        if (paid > ordered) {
            LOGGER.info("渠道侧金额高于订单金额，按实付结算, outTradeNo={}, ordered={}, paid={}",
                    order.getOutTradeNo(), ordered, paid);
        }
        return paid;
    }

    @Transactional(readOnly = true)
    Optional<PaymentOrderEntity> findByOutTradeNo(String outTradeNo) {
        if (!plausibleOutTradeNo(outTradeNo)) {
            return Optional.empty();
        }
        return Optional.ofNullable(orderMapper.selectOne(
                Wrappers.<PaymentOrderEntity>lambdaQuery()
                        .eq(PaymentOrderEntity::getOutTradeNo, outTradeNo)));
    }

    PaymentOrderEntity lockByOutTradeNo(String outTradeNo) {
        PaymentOrderEntity order = plausibleOutTradeNo(outTradeNo)
                ? orderMapper.selectOne(
                        Wrappers.<PaymentOrderEntity>lambdaQuery()
                                .eq(PaymentOrderEntity::getOutTradeNo, outTradeNo)
                                .last("FOR UPDATE"))
                : null;
        if (order == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "支付订单不存在");
        }
        return order;
    }

    /**
     * 超长或含非法字符时不必查库。
     *
     * <p>刻意保持宽松：现在生成的是 {@code GOLD-20260824-103512-K7Q3F9}，
     * 但存量订单还是早期的 Base64URL（可能以 {@code -}/{@code _} 开头、混大小写）。
     * 收紧成新格式会让那些老单子查不到——它们在渠道那边仍然是活的。</p>
     */
    private static boolean plausibleOutTradeNo(String outTradeNo) {
        return outTradeNo != null && OUT_TRADE_NO.matcher(outTradeNo).matches();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record SettlementOutcome(
            PaymentOrderStatus status,
            Long userAccountId,
            Long paymentRecordId,
            long amountMinor,
            boolean firstSettlement) {
    }
}
