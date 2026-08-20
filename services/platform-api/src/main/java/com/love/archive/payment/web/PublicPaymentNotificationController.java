package com.love.archive.payment.web;

import com.love.archive.common.web.ApiException;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.payment.application.NotifyPayload;
import com.love.archive.payment.application.OnlinePaymentService;
import com.love.archive.payment.domain.PaymentChannelType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 渠道支付结果通知。调用方是支付渠道而不是用户，因此不走会话校验，
 * 只靠平台公钥验签；验签失败返回 401，结算冲突返回 500 让渠道重试。
 */
@RestController
@RequestMapping("/api/v1/public/payment-notifications")
@RequiredArgsConstructor
public class PublicPaymentNotificationController {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PublicPaymentNotificationController.class);
    private static final Map<String, String> NOTIFY_ACCEPTED = Map.of("code", "SUCCESS");

    private final OnlinePaymentService onlinePaymentService;

    /** 易支付（XPay V2）结果通知，form 表单提交。 */
    @PostMapping("/xpay")
    public ResponseEntity<Map<String, String>> xpayNotification(HttpServletRequest request) {
        NotifyPayload payload = new NotifyPayload(Map.of(), extractParams(request), null);
        try {
            onlinePaymentService.handleNotification(PaymentChannelType.XPAY_ALIPAY, payload);
            return ResponseEntity.ok(NOTIFY_ACCEPTED);
        } catch (ApiException exception) {
            LOGGER.warn("支付回调处理失败, requestId={}, code={}",
                    RequestIdFilter.current(request), exception.code());
            return ResponseEntity.status(notifyStatus(exception))
                    .body(Map.of("code", "FAIL", "message", exception.getMessage()));
        }
    }

    private static Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0 && values[0] != null) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    /** 验签失败按 401 拒绝，其余交由渠道重试。 */
    private static HttpStatus notifyStatus(ApiException exception) {
        return "PAYMENT_NOTIFY_SIGNATURE_INVALID".equals(exception.code())
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
