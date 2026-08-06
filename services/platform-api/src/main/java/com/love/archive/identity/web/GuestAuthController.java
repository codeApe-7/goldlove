package com.love.archive.identity.web;

import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.identity.application.GuestAuthService;
import com.love.archive.identity.security.AuthLogics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guest/auth")
public class GuestAuthController {

    private final GuestAuthService guestAuthService;
    private final AuthLogics authLogics;

    public GuestAuthController(GuestAuthService guestAuthService, AuthLogics authLogics) {
        this.guestAuthService = guestAuthService;
        this.authLogics = authLogics;
    }

    @PostMapping("/activate")
    public ApiResponse<GuestSessionView> activate(
            @Valid @RequestBody ActivateGuestRequest body,
            HttpServletRequest request) {
        GuestSessionView session = guestAuthService.activate(
                body.phone(),
                body.initialCredential(),
                body.newPassword(),
                RequestIdFilter.current(request));
        return ApiResponse.success(session, RequestIdFilter.current(request));
    }

    @PostMapping("/login")
    public ApiResponse<GuestSessionView> login(
            @Valid @RequestBody GuestLoginRequest body,
            HttpServletRequest request) {
        GuestSessionView session = guestAuthService.authenticate(body.phone(), body.password());
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
}
