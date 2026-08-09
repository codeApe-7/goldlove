package com.love.archive.guest.web;

import com.love.archive.common.security.GuestAccountIdentity;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.guest.application.GuestProfileDraftService;
import com.love.archive.guest.application.GuestProfileDraftView;
import com.love.archive.guest.application.GuestFieldDefinitionQuery;
import com.love.archive.guest.application.GuestFieldDefinitionView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/guest/profile")
@RequiredArgsConstructor
public class GuestProfileController {

    private final GuestProfileDraftService profileService;
    private final GuestFieldDefinitionQuery fieldDefinitionQuery;
    private final GuestAccountIdentity guestIdentity;

    @GetMapping("/draft")
    public ApiResponse<GuestProfileDraftView> draft(HttpServletRequest request) {
        GuestProfileDraftView draft = profileService.get(guestIdentity.currentGuestAccountId());
        return ApiResponse.success(draft, RequestIdFilter.current(request));
    }

    @PutMapping("/draft")
    public ApiResponse<GuestProfileDraftView> save(
            @Valid @RequestBody SaveGuestProfileRequest body,
            HttpServletRequest request) {
        GuestProfileDraftView saved = profileService.save(
                guestIdentity.currentGuestAccountId(),
                body.toCommand(),
                RequestIdFilter.current(request));
        return ApiResponse.success(saved, RequestIdFilter.current(request));
    }

    @GetMapping("/status")
    public ApiResponse<GuestProfileStatusView> status(HttpServletRequest request) {
        GuestProfileDraftView draft = profileService.get(guestIdentity.currentGuestAccountId());
        return ApiResponse.success(
                GuestProfileStatusView.from(draft), RequestIdFilter.current(request));
    }

    @GetMapping("/field-definitions")
    public ApiResponse<List<GuestFieldDefinitionView>> fieldDefinitions(HttpServletRequest request) {
        return ApiResponse.success(
                fieldDefinitionQuery.listEnabled(), RequestIdFilter.current(request));
    }
}
