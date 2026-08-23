package com.love.archive.payment.web;

import jakarta.validation.constraints.NotNull;

/**
 * 金额一律以「分」传输，与库里和渠道一致，避免小数在传输层来回换算。
 *
 * <p>范围校验故意放在 {@code PaymentSettingService} 里而不是用 {@code @Min}/{@code @Max}：
 * {@code GlobalExceptionHandler} 对 bean validation 失败统一回「请求参数不正确」，
 * 而金额超范围应该告诉人具体的上下限。</p>
 */
public record UpdatePaymentSettingRequest(
        @NotNull Long vipUpgradeAmountMinor) {
}
