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
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
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
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);
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
        if (result.totalAmountMinor() != order.getAmountMinor()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PAYMENT_AMOUNT_MISMATCH", "支付金额与订单金额不一致");
        }
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
        payment.setAmountMinor(order.getAmountMinor());
        payment.setCurrency("CNY");
        payment.setStatus(PaymentStatus.PAID);
        payment.setMembershipCreditMinor(0L);
        payment.setPaidAt(paidAt);
        payment.setCreatedAt(now);
        paymentRecordMapper.insert(payment);

        int updated = orderMapper.update(Wrappers.<PaymentOrderEntity>lambdaUpdate()
                .eq(PaymentOrderEntity::getId, order.getId())
                .eq(PaymentOrderEntity::getStatus, PaymentOrderStatus.CREATED)
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
                "{\"outTradeNo\":\"" + order.getOutTradeNo() + "\"}",
                now));
        return new SettlementOutcome(
                PaymentOrderStatus.PAID, order.getUserAccountId(), payment.getId(),
                order.getAmountMinor(), true);
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

    /** 商户订单号形如 Base64URL，超长或含非法字符时不必查库。 */
    private static boolean plausibleOutTradeNo(String outTradeNo) {
        return outTradeNo != null && OUT_TRADE_NO.matcher(outTradeNo).matches();
    }

    record SettlementOutcome(
            PaymentOrderStatus status,
            Long userAccountId,
            Long paymentRecordId,
            long amountMinor,
            boolean firstSettlement) {
    }
}
