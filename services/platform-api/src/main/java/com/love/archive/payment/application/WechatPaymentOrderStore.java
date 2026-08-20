package com.love.archive.payment.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.security.SensitiveValueProtector;
import com.love.archive.common.web.ApiException;
import com.love.archive.payment.domain.PaymentChannelType;
import com.love.archive.payment.domain.PaymentStatus;
import com.love.archive.payment.domain.WechatOrderStatus;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.PaymentRecordMapper;
import com.love.archive.payment.persistence.WechatPaymentOrderEntity;
import com.love.archive.payment.persistence.WechatPaymentOrderMapper;
import com.love.archive.payment.application.PaymentResult;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 线上支付订单落库。事务保持在数据库操作范围内，渠道出网调用留在
 * {@link OnlinePaymentService}，避免长时间占用连接池。
 */
@Service
@RequiredArgsConstructor
class WechatPaymentOrderStore {

    static final String OPENID_DOMAIN = "wechat:openid";
    static final String TRANSACTION_DOMAIN = "wechat:transaction";

    private static final java.util.regex.Pattern OUT_TRADE_NO =
            java.util.regex.Pattern.compile("[A-Za-z0-9_-]{6,64}");

    private final WechatPaymentOrderMapper orderMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final SensitiveValueProtector protector;
    private final AuditTrail auditTrail;

    @Transactional
    void insertCreated(
            String outTradeNo,
            String payer,
            PaymentChannelType channel,
            long presentedAuthorizationDocumentId,
            long amountMinor,
            String description) {
        OffsetDateTime now = OffsetDateTime.now();
        WechatPaymentOrderEntity order = new WechatPaymentOrderEntity();
        order.setOutTradeNo(outTradeNo);
        order.setChannel(channel);
        if (payer != null) {
            order.setOpenidCiphertext(protector.encrypt(OPENID_DOMAIN, payer));
            order.setOpenidHmac(protector.hmac(OPENID_DOMAIN, payer));
        }
        order.setDescription(description);
        order.setAmountMinor(amountMinor);
        order.setCurrency("CNY");
        order.setStatus(WechatOrderStatus.CREATED);
        order.setPresentedAuthorizationDocumentId(presentedAuthorizationDocumentId);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);
    }

    @Transactional
    void attachPrepayId(String outTradeNo, String prepayId) {
        orderMapper.update(Wrappers.<WechatPaymentOrderEntity>lambdaUpdate()
                .eq(WechatPaymentOrderEntity::getOutTradeNo, outTradeNo)
                .eq(WechatPaymentOrderEntity::getStatus, WechatOrderStatus.CREATED)
                .set(WechatPaymentOrderEntity::getPrepayId, prepayId)
                .set(WechatPaymentOrderEntity::getUpdatedAt, OffsetDateTime.now()));
    }

    /**
     * 幂等结算：首次收到支付成功时写付款记录并推进订单；重复回调直接返回已有结果。
     */
    @Transactional
    SettlementOutcome settle(PaymentResult result) {
        WechatPaymentOrderEntity order = lockByOutTradeNo(result.outTradeNo());
        if (order.getStatus() == WechatOrderStatus.PAID
                || order.getStatus() == WechatOrderStatus.REFUNDED) {
            return new SettlementOutcome(order.getStatus(), order.getPaymentRecordId(), false);
        }
        if (!result.paid()) {
            return new SettlementOutcome(order.getStatus(), null, false);
        }
        if (result.totalAmountMinor() != order.getAmountMinor()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PAYMENT_AMOUNT_MISMATCH", "支付金额与订单金额不一致");
        }
        if (result.payer() != null
                && !protector.hmac(OPENID_DOMAIN, result.payer()).equals(order.getOpenidHmac())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PAYMENT_ORDER_PAYER_MISMATCH", "支付者与下单人不一致");
        }
        if (result.transactionId() == null || result.transactionId().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_RESPONSE_INVALID", "支付渠道响应缺少交易号");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime paidAt = result.successTime() == null ? now : result.successTime();
        long paidAmountMinor = Math.min(result.paidAmountMinor(), order.getAmountMinor());
        byte[] transactionCiphertext = protector.encrypt(TRANSACTION_DOMAIN, result.transactionId());
        String transactionHmac = protector.hmac(TRANSACTION_DOMAIN, result.transactionId());

        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setPaymentReference(order.getOutTradeNo());
        payment.setAmountMinor(order.getAmountMinor());
        payment.setCurrency("CNY");
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaymentChannel(order.getChannel());
        payment.setOutTradeNo(order.getOutTradeNo());
        payment.setTransactionIdCiphertext(transactionCiphertext);
        payment.setTransactionIdHmac(transactionHmac);
        payment.setPaidAmountMinor(paidAmountMinor);
        payment.setMembershipCreditMinor(0L);
        payment.setRegistered(false);
        payment.setPaidAt(paidAt);
        payment.setPresentedAuthorizationDocumentId(order.getPresentedAuthorizationDocumentId());
        payment.setCreatedAt(now);
        paymentRecordMapper.insert(payment);

        int updated = orderMapper.update(Wrappers.<WechatPaymentOrderEntity>lambdaUpdate()
                .eq(WechatPaymentOrderEntity::getId, order.getId())
                .eq(WechatPaymentOrderEntity::getStatus, WechatOrderStatus.CREATED)
                .set(WechatPaymentOrderEntity::getStatus, WechatOrderStatus.PAID)
                .set(WechatPaymentOrderEntity::getTransactionIdCiphertext, transactionCiphertext)
                .set(WechatPaymentOrderEntity::getTransactionIdHmac, transactionHmac)
                .set(WechatPaymentOrderEntity::getPaidAmountMinor, paidAmountMinor)
                .set(WechatPaymentOrderEntity::getPaymentRecordId, payment.getId())
                .set(WechatPaymentOrderEntity::getPaidAt, paidAt)
                .set(WechatPaymentOrderEntity::getUpdatedAt, now));
        if (updated != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PAYMENT_ORDER_STATE_CONFLICT", "订单状态已变化，请重试");
        }

        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.SYSTEM,
                null,
                "ONLINE_PAYMENT_SETTLED",
                "PAYMENT_RECORD",
                payment.getId(),
                null,
                "{\"outTradeNo\":\"" + order.getOutTradeNo() + "\"}",
                now));
        return new SettlementOutcome(WechatOrderStatus.PAID, payment.getId(), true);
    }

    @Transactional(readOnly = true)
    Optional<WechatPaymentOrderEntity> findByOutTradeNo(String outTradeNo) {
        if (!plausibleOutTradeNo(outTradeNo)) {
            return Optional.empty();
        }
        return Optional.ofNullable(orderMapper.selectOne(
                Wrappers.<WechatPaymentOrderEntity>lambdaQuery()
                        .eq(WechatPaymentOrderEntity::getOutTradeNo, outTradeNo)));
    }

    WechatPaymentOrderEntity lockByOutTradeNo(String outTradeNo) {
        WechatPaymentOrderEntity order = plausibleOutTradeNo(outTradeNo)
                ? orderMapper.selectOne(
                        Wrappers.<WechatPaymentOrderEntity>lambdaQuery()
                                .eq(WechatPaymentOrderEntity::getOutTradeNo, outTradeNo)
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

    PaymentRecordEntity requirePaymentRecord(Long paymentRecordId) {
        PaymentRecordEntity payment = paymentRecordId == null
                ? null
                : paymentRecordMapper.selectById(paymentRecordId);
        if (payment == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "支付订单不存在");
        }
        return payment;
    }

    /**
     * 把线上付款记录绑定到新建账号并标记为已注册；重复注册返回 false。
     */
    int bindRegisteredAccount(long paymentRecordId, long userAccountId) {
        return paymentRecordMapper.update(Wrappers.<PaymentRecordEntity>lambdaUpdate()
                .eq(PaymentRecordEntity::getId, paymentRecordId)
                .eq(PaymentRecordEntity::getRegistered, false)
                .isNull(PaymentRecordEntity::getUserAccountId)
                .set(PaymentRecordEntity::getUserAccountId, userAccountId)
                .set(PaymentRecordEntity::getRegistered, true));
    }

    String decryptOpenid(byte[] ciphertext) {
        return protector.decrypt(OPENID_DOMAIN, ciphertext);
    }

    String hmacToken(String rawToken) {
        return protector.hmac("registration:token", rawToken);
    }

    byte[] encryptOpenid(String openid) {
        return protector.encrypt(OPENID_DOMAIN, openid);
    }

    String hmacOpenid(String openid) {
        return protector.hmac(OPENID_DOMAIN, openid);
    }

    record SettlementOutcome(WechatOrderStatus status, Long paymentRecordId, boolean firstSettlement) {
    }
}
