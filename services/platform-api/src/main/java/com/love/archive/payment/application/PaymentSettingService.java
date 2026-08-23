package com.love.archive.payment.application;

import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.web.ApiException;
import com.love.archive.payment.config.OnlinePaymentProperties;
import com.love.archive.payment.persistence.PaymentSettingEntity;
import com.love.archive.payment.persistence.PaymentSettingMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * VIP 升级金额的读写。
 *
 * <p>金额有两个来源，库里那一行**覆盖**配置：后台没设过就用
 * {@code VIP_UPGRADE_AMOUNT_MINOR}，设过之后一律用库里的值。之所以不在迁移里
 * 直接种一行，是因为种什么值都会改掉某个环境的现价（线上是 ¥0.01，配置默认是 ¥1.00）。</p>
 *
 * <p>改价只影响之后创建的订单。已经创建的订单把金额记在 {@code payment_order.amount_minor} 上，
 * 回调结算时按订单金额核对，所以改价不会让在途订单对不上账。</p>
 */
@Service
@RequiredArgsConstructor
public class PaymentSettingService {

    /** 单行表的固定主键。 */
    private static final int SINGLETON_ID = 1;

    /** 与 V5 迁移里的 CHECK 保持一致：¥0.01 ~ ¥100000.00。 */
    public static final long MIN_AMOUNT_MINOR = 1L;
    public static final long MAX_AMOUNT_MINOR = 10_000_000L;

    private final PaymentSettingMapper paymentSettingMapper;
    private final OnlinePaymentProperties properties;
    private final AuditTrail auditTrail;
    private final ObjectMapper objectMapper;

    /** 下单与前端展示都取这个值。 */
    public long vipUpgradeAmountMinor() {
        PaymentSettingEntity stored = stored();
        return stored == null ? properties.getVipUpgradeAmountMinor() : stored.getVipUpgradeAmountMinor();
    }

    public PaymentSettingView view() {
        PaymentSettingEntity stored = stored();
        long configured = properties.getVipUpgradeAmountMinor();
        return new PaymentSettingView(
                stored == null ? configured : stored.getVipUpgradeAmountMinor(),
                configured,
                stored != null,
                stored == null ? null : stored.getUpdatedAt(),
                properties.getOrderDescription(),
                MIN_AMOUNT_MINOR,
                MAX_AMOUNT_MINOR);
    }

    @Transactional
    public PaymentSettingView update(long amountMinor, long adminId, String requestId) {
        if (amountMinor < MIN_AMOUNT_MINOR || amountMinor > MAX_AMOUNT_MINOR) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PAYMENT_AMOUNT_INVALID",
                    "支付金额需在 " + yuan(MIN_AMOUNT_MINOR) + " 与 " + yuan(MAX_AMOUNT_MINOR) + " 之间");
        }

        PaymentSettingEntity before = stored();
        if (before != null && before.getVipUpgradeAmountMinor() == amountMinor) {
            // 幂等：值没变就不写库、也不多留一条审计。
            return view();
        }

        OffsetDateTime now = OffsetDateTime.now();
        paymentSettingMapper.upsert(amountMinor, adminId, now);
        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.ADMIN,
                adminId,
                "PAYMENT_AMOUNT_UPDATED",
                "PAYMENT_SETTING",
                (long) SINGLETON_ID,
                requestId,
                metadata(before, amountMinor),
                now));
        return view();
    }

    private PaymentSettingEntity stored() {
        return paymentSettingMapper.selectById(SINGLETON_ID);
    }

    /** 审计里记清楚「从多少改到多少」，以及改之前用的是配置值还是后台值。 */
    private String metadata(PaymentSettingEntity before, long toAmountMinor) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(
                "fromAmountMinor",
                before == null ? properties.getVipUpgradeAmountMinor() : before.getVipUpgradeAmountMinor());
        metadata.put("fromSource", before == null ? "CONFIG" : "ADMIN");
        metadata.put("toAmountMinor", toAmountMinor);
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException error) {
            return "{\"toAmountMinor\":" + toAmountMinor + "}";
        }
    }

    private static String yuan(long amountMinor) {
        return "¥" + BigDecimal.valueOf(amountMinor, 2).toPlainString();
    }
}
