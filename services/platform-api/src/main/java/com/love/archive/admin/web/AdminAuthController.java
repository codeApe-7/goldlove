package com.love.archive.admin.web;

import com.love.archive.admin.application.AdminAuthService;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.identity.security.AuthLogics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AuthLogics authLogics;

    public AdminAuthController(AdminAuthService adminAuthService, AuthLogics authLogics) {
        this.adminAuthService = adminAuthService;
        this.authLogics = authLogics;
    }

    @PostMapping("/login")
    public ApiResponse<AdminSessionView> login(
            @Valid @RequestBody AdminLoginRequest body,
            HttpServletRequest request) {
        AdminSessionView session = adminAuthService.authenticate(body.username(), body.password());
        authLogics.admin().login(session.id());
        return ApiResponse.success(session, RequestIdFilter.current(request));
    }
}
