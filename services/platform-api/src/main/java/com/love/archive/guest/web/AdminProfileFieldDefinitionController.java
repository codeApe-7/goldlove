package com.love.archive.guest.web;

import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.PageView;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.guest.application.ProfileFieldDefinitionService;
import com.love.archive.guest.application.ProfileFieldDefinitionView;
import com.love.archive.identity.security.AuthLogics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/profile-field-definitions")
@RequiredArgsConstructor
public class AdminProfileFieldDefinitionController {

    private final ProfileFieldDefinitionService definitionService;
    private final AuthLogics authLogics;

    @GetMapping
    public ApiResponse<PageView<ProfileFieldDefinitionView>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            HttpServletRequest request) {
        return ApiResponse.success(
                definitionService.list(page, size), RequestIdFilter.current(request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProfileFieldDefinitionView>> create(
            @Valid @RequestBody CreateProfileFieldDefinitionRequest body,
            HttpServletRequest request) {
        ProfileFieldDefinitionView created = definitionService.create(
                authLogics.admin().getLoginIdAsLong(),
                body.toCommand(),
                RequestIdFilter.current(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, RequestIdFilter.current(request)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ProfileFieldDefinitionView> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateProfileFieldDefinitionRequest body,
            HttpServletRequest request) {
        ProfileFieldDefinitionView updated = definitionService.update(
                authLogics.admin().getLoginIdAsLong(),
                id,
                body.fieldCode(),
                body.storageKind(),
                body.dataType(),
                body.toCommand(),
                RequestIdFilter.current(request));
        return ApiResponse.success(updated, RequestIdFilter.current(request));
    }
}
