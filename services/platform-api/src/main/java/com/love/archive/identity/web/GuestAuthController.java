package com.love.archive.identity.web;

import cn.dev33.satoken.stp.StpLogic;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.identity.application.GuestAuthService;
import com.love.archive.identity.application.SelfRegistrationCommand;
import com.love.archive.identity.application.SelfRegistrationService;
import com.love.archive.identity.domain.PhoneNormalizer;
import com.love.archive.identity.security.AuthLogics;
import com.love.archive.identity.security.AuthenticationAttemptLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guest/auth")
@RequiredArgsConstructor
public class GuestAuthController {

    private final GuestAuthService guestAuthService;
    private final SelfRegistrationService selfRegistrationService;
    private final AuthLogics authLogics;
    private final AuthenticationAttemptLimiter attemptLimiter;
    private final PhoneNormalizer phoneNormalizer;

    @PostMapping("/register")
    public ApiResponse<GuestSessionView> register(
            @Valid @RequestBody SelfRegistrationRequest body,
            HttpServletRequest request) {
        // 注册是公开写接口，按手机号 + 来源地址双维度限流。
        // 成功后刻意不重置手机号计数：同一号码只能注册一次，重复请求都是异常流量。
        attemptLimiter.checkAndConsume(
                "guest-register", normalizeForRateLimit(body.phone()), request.getRemoteAddr());
        GuestSessionView session = selfRegistrationService.register(new SelfRegistrationCommand(
                body.phone(),
                body.password(),
                body.confirmPassword(),
                body.acceptedAuthorization(),
                body.authorizationDocumentVersion(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                RequestIdFilter.current(request)));
        return ApiResponse.success(withSessionToken(session), RequestIdFilter.current(request));
    }

    @PostMapping("/login")
    public ApiResponse<GuestSessionView> login(
            @Valid @RequestBody GuestLoginRequest body,
            HttpServletRequest request) {
        String rateLimitPhone = normalizeForRateLimit(body.phone());
        attemptLimiter.checkAndConsume("guest-login", rateLimitPhone, request.getRemoteAddr());
        GuestSessionView session = guestAuthService.authenticate(body.phone(), body.password());
        attemptLimiter.resetAccount("guest-login", rateLimitPhone);
        return ApiResponse.success(withSessionToken(session), RequestIdFilter.current(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authLogics.guest().logout();
        return ApiResponse.success(null, RequestIdFilter.current(request));
    }

    @GetMapping("/me")
    public ApiResponse<GuestSessionView> me(HttpServletRequest request) {
        GuestSessionView session = guestAuthService.getSession(authLogics.guest().getLoginIdAsLong());
        return ApiResponse.success(session, RequestIdFilter.current(request));
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
                session.accountId(),
                session.status(),
                session.membershipTier(),
                logic.getTokenValue(),
                logic.getTokenTimeout());
    }
}
