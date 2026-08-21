package com.love.archive.admin.web;

import com.love.archive.admin.application.AdminActivationCodeItem;
import com.love.archive.admin.application.AdminLedgerQueryService;
import com.love.archive.admin.application.AdminPaymentOrderItem;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.PageView;
import com.love.archive.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端只读账目：支付订单与激活码列表。激活码的生成与作废在 identity 模块。 */
@RestController
@RequiredArgsConstructor
public class AdminLedgerController {

    private final AdminLedgerQueryService ledgerQueryService;

    @GetMapping("/api/v1/admin/payment-orders")
    public ApiResponse<PageView<AdminPaymentOrderItem>> orders(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        return ApiResponse.success(
                ledgerQueryService.listOrders(phone, status, page, size),
                RequestIdFilter.current(request));
    }

    @GetMapping("/api/v1/admin/activation-codes")
    public ApiResponse<PageView<AdminActivationCodeItem>> activationCodes(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        return ApiResponse.success(
                ledgerQueryService.listActivationCodes(phone, status, page, size),
                RequestIdFilter.current(request));
    }
}
