package com.love.archive.identity.web;

import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.identity.application.GuestAuthService;
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
    private final AuthLogics authLogics;
    private final AuthenticationAttemptLimiter attemptLimiter;
    private final PhoneNormalizer phoneNormalizer;

    @PostMapping("/activate")
    public ApiResponse<GuestSessionView> activate(
            @Valid @RequestBody ActivateGuestRequest body,
            HttpServletRequest request) {
        String rateLimitPhone = normalizeForRateLimit(body.phone());
        attemptLimiter.checkAndConsume("guest-activation", rateLimitPhone, request.getRemoteAddr());
        GuestSessionView session = guestAuthService.activate(
                body.phone(),
                body.initialCredential(),
                body.newPassword(),
                RequestIdFilter.current(request));
        attemptLimiter.resetAccount("guest-activation", rateLimitPhone);
        return ApiResponse.success(session, RequestIdFilter.current(request));
    }

    @PostMapping("/login")
    public ApiResponse<GuestSessionView> login(
            @Valid @RequestBody GuestLoginRequest body,
            HttpServletRequest request) {
        String rateLimitPhone = normalizeForRateLimit(body.phone());
        attemptLimiter.checkAndConsume("guest-login", rateLimitPhone, request.getRemoteAddr());
        GuestSessionView session = guestAuthService.authenticate(body.phone(), body.password());
        attemptLimiter.resetAccount("guest-login", rateLimitPhone);
        authLogics.guest().login(session.accountId());
        return ApiResponse.success(session, RequestIdFilter.current(request));
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
}
