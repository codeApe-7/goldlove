package com.love.archive.admin.web;

import com.love.archive.admin.application.AdminDashboardService;
import com.love.archive.admin.application.AdminDashboardView;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping("/stats")
    public ApiResponse<AdminDashboardView> stats(HttpServletRequest request) {
        return ApiResponse.success(
                dashboardService.stats(), RequestIdFilter.current(request));
    }
}
