package com.love.archive.payment.web;

import com.love.archive.common.security.GuestAccountIdentity;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.payment.application.OnlineOrderStatusView;
import com.love.archive.payment.application.OnlineOrderView;
import com.love.archive.payment.application.OnlinePaymentService;
import com.love.archive.payment.application.OnlinePaymentSettingsView;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * VIP 升级支付：下单与查单都要求已登录，订单直接挂在当前账号上。
 * 前端不传金额也不传账号，两者都由服务端决定。
 */
@RestController
@RequestMapping("/api/v1/guest/vip-payments")
@RequiredArgsConstructor
public class GuestVipPaymentController {

    private final OnlinePaymentService onlinePaymentService;
    private final GuestAccountIdentity guestIdentity;

    @GetMapping("/settings")
    public ApiResponse<OnlinePaymentSettingsView> settings(HttpServletRequest request) {
        return ApiResponse.success(
                onlinePaymentService.settings(), RequestIdFilter.current(request));
    }

    @PostMapping("/orders")
    public ApiResponse<OnlineOrderView> createOrder(HttpServletRequest request) {
        OnlineOrderView order = onlinePaymentService.createOrder(
                guestIdentity.currentGuestAccountId());
        return ApiResponse.success(order, RequestIdFilter.current(request));
    }

    @GetMapping("/orders/{outTradeNo}")
    public ApiResponse<OnlineOrderStatusView> status(
            @PathVariable String outTradeNo,
            HttpServletRequest request) {
        OnlineOrderStatusView status = onlinePaymentService.status(
                guestIdentity.currentGuestAccountId(), outTradeNo);
        return ApiResponse.success(status, RequestIdFilter.current(request));
    }
}
