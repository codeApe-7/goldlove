package com.love.archive.payment.application;

import java.time.OffsetDateTime;

/**
 * 支付参数的后台视图。
 *
 * @param vipUpgradeAmountMinor 当前生效金额（分）——访客下单用的就是这个数
 * @param configuredAmountMinor 配置（环境变量）里的金额，后台没设过时的回落值
 * @param managedInAdmin        true 表示当前生效值来自后台设置，false 表示还在用配置值
 * @param updatedAt             后台最近一次修改时间，从未改过为 null
 * @param orderDescription      下单商品描述，只读——仍由 VIP_UPGRADE_DESCRIPTION 决定
 * @param minAmountMinor        允许的最小金额（分），与库里的 CHECK 一致
 * @param maxAmountMinor        允许的最大金额（分），与库里的 CHECK 一致
 */
public record PaymentSettingView(
        long vipUpgradeAmountMinor,
        long configuredAmountMinor,
        boolean managedInAdmin,
        OffsetDateTime updatedAt,
        String orderDescription,
        long minAmountMinor,
        long maxAmountMinor) {
}
