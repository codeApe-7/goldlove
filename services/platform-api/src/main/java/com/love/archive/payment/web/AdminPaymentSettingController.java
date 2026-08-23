package com.love.archive.payment.web;

import com.love.archive.common.security.AdminIdentity;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.payment.application.PaymentSettingService;
import com.love.archive.payment.application.PaymentSettingView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台维护支付金额。控制器放在 payment 模块——金额是这个模块的数据，
 * 与激活码（identity）、字段定义（guest）同一套做法：写操作在数据所属模块，
 * 只读列表才在 admin。
 *
 * <p>拿当前管理员 id 走 {@link AdminIdentity} 端口，不直接依赖 identity：
 * payment 的 allowedDependencies 里没有 identity，加进去就会成环。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/payment-settings")
@RequiredArgsConstructor
public class AdminPaymentSettingController {

    private final PaymentSettingService paymentSettingService;
    private final AdminIdentity adminIdentity;

    @GetMapping
    public ApiResponse<PaymentSettingView> current(HttpServletRequest request) {
        return ApiResponse.success(paymentSettingService.view(), RequestIdFilter.current(request));
    }

    @PutMapping
    public ApiResponse<PaymentSettingView> update(
            @Valid @RequestBody UpdatePaymentSettingRequest body,
            HttpServletRequest request) {
        PaymentSettingView view = paymentSettingService.update(
                body.vipUpgradeAmountMinor(),
                adminIdentity.currentAdminId(),
                RequestIdFilter.current(request));
        return ApiResponse.success(view, RequestIdFilter.current(request));
    }
}
