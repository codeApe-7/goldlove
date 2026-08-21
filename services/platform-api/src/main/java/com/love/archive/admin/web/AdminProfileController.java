package com.love.archive.admin.web;

import com.love.archive.admin.application.AdminProfileDetail;
import com.love.archive.admin.application.AdminProfileFilter;
import com.love.archive.admin.application.AdminProfileListItem;
import com.love.archive.admin.application.AdminProfileQueryService;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.PageView;
import com.love.archive.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/profiles")
@RequiredArgsConstructor
public class AdminProfileController {

    private final AdminProfileQueryService profileQueryService;

    @GetMapping
    public ApiResponse<PageView<AdminProfileListItem>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String membershipTier,
            HttpServletRequest request) {
        PageView<AdminProfileListItem> result = profileQueryService.list(
                AdminProfileFilter.of(phone, status, membershipTier), page, size);
        return ApiResponse.success(result, RequestIdFilter.current(request));
    }

    @GetMapping("/{profileId}")
    public ApiResponse<AdminProfileDetail> detail(
            @PathVariable long profileId,
            HttpServletRequest request) {
        return ApiResponse.success(
                profileQueryService.detail(profileId), RequestIdFilter.current(request));
    }
}
