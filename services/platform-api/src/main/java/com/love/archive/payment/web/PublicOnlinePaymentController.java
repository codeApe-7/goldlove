package com.love.archive.payment.web;

import com.love.archive.common.web.ApiException;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.payment.application.IssuedRegistrationToken;
import com.love.archive.payment.application.OnlineOrderStatusView;
import com.love.archive.payment.application.OnlineOrderView;
import com.love.archive.payment.application.OnlinePaymentService;
import com.love.archive.payment.application.OnlinePaymentSettingsView;
import com.love.archive.payment.application.RegistrationTokenService;
import com.love.archive.wechatpay.application.NotifyPayload;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.util.Base64;
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
        return ApiResponse.success(
                new OnlinePaymentSettingsResponse(
                        settings.appId(),
                        settings.registrationAmountMinor(),
                        settings.orderDescription(),
                        onlinePaymentService.authorizeUrl(state),
                        state),
                RequestIdFilter.current(request));
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<OnlineOrderView>> createOrder(
            @Valid @RequestBody CreateOnlineOrderRequest body,
            HttpServletRequest request) {
        OnlineOrderView order = onlinePaymentService.createOrder(
                body.authorizationCode(), body.authorizationDocumentVersion());
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
    @PostMapping("/notifications")
    public ResponseEntity<Map<String, String>> notification(
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        try {
            onlinePaymentService.handleNotification(
                    new NotifyPayload(serial, timestamp, nonce, signature, body));
            return ResponseEntity.ok(NOTIFY_ACCEPTED);
        } catch (ApiException exception) {
            LOGGER.warn("支付回调处理失败, requestId={}, code={}",
                    RequestIdFilter.current(request), exception.code());
            return ResponseEntity.status(notifyStatus(exception))
                    .body(Map.of("code", "FAIL", "message", exception.getMessage()));
        }
    }

    /** 验签失败按 401 拒绝，其余交由微信重试。 */
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
}
