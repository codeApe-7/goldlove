package com.love.archive.payment.web;

import com.love.archive.common.web.ApiException;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.payment.application.IssuedRegistrationToken;
import com.love.archive.payment.application.NotifyPayload;
import com.love.archive.payment.application.OnlineOrderStatusView;
import com.love.archive.payment.application.OnlineOrderView;
import com.love.archive.payment.application.OnlinePaymentService;
import com.love.archive.payment.application.OnlinePaymentSettingsView;
import com.love.archive.payment.application.RegistrationTokenService;
import com.love.archive.payment.domain.PaymentChannelType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 线上支付公开端点。注册前用户尚无账号，因此挂在 /api/v1/public 下。
 * 回调端点按微信支付要求返回 {@code {"code":"SUCCESS"}} 而非统一响应包装。
 */
@RestController
@RequestMapping("/api/v1/public/online-payments")
@RequiredArgsConstructor
public class PublicOnlinePaymentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicOnlinePaymentController.class);
    private static final int STATE_RANDOM_BYTES = 16;
    private static final Map<String, String> NOTIFY_ACCEPTED =
            Map.of("code", "SUCCESS", "message", "成功");

    private final OnlinePaymentService onlinePaymentService;
    private final RegistrationTokenService registrationTokenService;
    private final SecureRandom secureRandom;

    @GetMapping("/settings")
    public ApiResponse<OnlinePaymentSettingsResponse> settings(HttpServletRequest request) {
        OnlinePaymentSettingsView settings = onlinePaymentService.settings();
        String state = randomState();
        String authorizeUrl = onlinePaymentService.requiresPayerAuthorization()
                ? onlinePaymentService.payerAuthorizationUrl(state)
                : null;
        return ApiResponse.success(
                new OnlinePaymentSettingsResponse(
                        settings.channelType(),
                        settings.amountMinor(),
                        settings.orderDescription(),
                        authorizeUrl,
                        state),
                RequestIdFilter.current(request));
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<OnlineOrderView>> createOrder(
            @Valid @RequestBody CreateOnlineOrderRequest body,
            HttpServletRequest request) {
        OnlineOrderView order = onlinePaymentService.createOrder(
                body.authorizationCode(),
                body.authorizationDocumentVersion(),
                body.phone(),
                request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(order, RequestIdFilter.current(request)));
    }

    @GetMapping("/orders/{outTradeNo}")
    public ApiResponse<OnlineOrderStatusView> orderStatus(
            @PathVariable String outTradeNo,
            HttpServletRequest request) {
        return ApiResponse.success(
                onlinePaymentService.status(outTradeNo), RequestIdFilter.current(request));
    }

    @PostMapping("/orders/{outTradeNo}/registration-tokens")
    public ResponseEntity<ApiResponse<IssuedRegistrationToken>> issueRegistrationToken(
            @PathVariable String outTradeNo,
            HttpServletRequest request) {
        IssuedRegistrationToken token = registrationTokenService.issue(outTradeNo);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(token, RequestIdFilter.current(request)));
    }

    /**
     * 微信支付结果通知。验签失败或结算冲突时按微信要求返回失败体，微信会重试。
     */
    @PostMapping("/notifications/wechat")
    public ResponseEntity<Map<String, String>> wechatNotification(
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        putIfPresent(headers, "Wechatpay-Serial", serial);
        putIfPresent(headers, "Wechatpay-Timestamp", timestamp);
        putIfPresent(headers, "Wechatpay-Nonce", nonce);
        putIfPresent(headers, "Wechatpay-Signature", signature);
        return handleNotification(
                PaymentChannelType.WECHAT_JSAPI, new NotifyPayload(headers, Map.of(), body), request);
    }

    /**
     * 易支付（XPay V2）结果通知，form 表单提交。
     */
    @PostMapping("/notifications/xpay")
    public ResponseEntity<Map<String, String>> xpayNotification(HttpServletRequest request) {
        return handleNotification(
                PaymentChannelType.XPAY_ALIPAY, new NotifyPayload(Map.of(), extractParams(request), null), request);
    }

    private ResponseEntity<Map<String, String>> handleNotification(
            PaymentChannelType channelType, NotifyPayload payload, HttpServletRequest request) {
        try {
            onlinePaymentService.handleNotification(channelType, payload);
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

    private String randomState() {
        byte[] random = new byte[STATE_RANDOM_BYTES];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
