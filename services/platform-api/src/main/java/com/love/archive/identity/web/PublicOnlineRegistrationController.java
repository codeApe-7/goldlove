package com.love.archive.identity.web;

import cn.dev33.satoken.stp.StpLogic;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.identity.application.OnlineRegistrationCommand;
import com.love.archive.identity.application.OnlineRegistrationService;
import com.love.archive.identity.domain.PhoneNormalizer;
import com.love.archive.identity.security.AuthLogics;
import com.love.archive.identity.security.AuthenticationAttemptLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 线上注册端点。注册前用户尚无账号与会话，因此挂在 /api/v1/public 下；
 * 注册成功直接返回访客会话令牌，前端随后复用现有授权书与档案流程。
 */
@RestController
@RequestMapping("/api/v1/public/registrations")
@RequiredArgsConstructor
public class PublicOnlineRegistrationController {

    private final OnlineRegistrationService onlineRegistrationService;
    private final AuthLogics authLogics;
    private final AuthenticationAttemptLimiter attemptLimiter;
    private final PhoneNormalizer phoneNormalizer;

    @PostMapping
    public ResponseEntity<ApiResponse<GuestSessionView>> register(
            @Valid @RequestBody OnlineRegistrationRequest body,
            HttpServletRequest request) {
        String rateLimitPhone = normalizeForRateLimit(body.phone());
        attemptLimiter.checkAndConsume("guest-online-registration", rateLimitPhone, request.getRemoteAddr());
        GuestSessionView session = onlineRegistrationService.register(new OnlineRegistrationCommand(
                body.registrationToken(),
                body.phone(),
                body.password(),
                RequestIdFilter.current(request)));
        attemptLimiter.resetAccount("guest-online-registration", rateLimitPhone);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(withSessionToken(session), RequestIdFilter.current(request)));
    }

    private String normalizeForRateLimit(String rawPhone) {
        try {
            return phoneNormalizer.normalize(rawPhone);
        } catch (IllegalArgumentException exception) {
            return rawPhone;
        }
    }

    private GuestSessionView withSessionToken(GuestSessionView session) {
        authLogics.guest().login(session.accountId());
        StpLogic logic = authLogics.guest();
        return new GuestSessionView(
                session.accountId(), session.status(), logic.getTokenValue(), logic.getTokenTimeout());
    }
}
