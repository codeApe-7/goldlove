package com.love.archive.identity.web;

import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.identity.application.AccountAdministrationService;
import com.love.archive.identity.application.AccountStatusView;
import com.love.archive.identity.security.AuthLogics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员停用 / 启用访客账号。控制器放在 identity 模块——账号是这个模块的数据，
 * 与 {@link AdminActivationCodeController} 同一套做法（列表查询在 admin 模块，写操作在数据所属模块）。
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountStatusController {

    private final AccountAdministrationService accountAdministrationService;
    private final AuthLogics authLogics;

    @PostMapping("/{accountId}/suspend")
    public ApiResponse<AccountStatusView> suspend(
            @PathVariable long accountId,
            @Valid @RequestBody(required = false) UpdateAccountStatusRequest body,
            HttpServletRequest request) {
        AccountStatusView view = accountAdministrationService.suspend(
                accountId,
                body == null ? null : body.reason(),
                authLogics.admin().getLoginIdAsLong(),
                RequestIdFilter.current(request));
        return ApiResponse.success(view, RequestIdFilter.current(request));
    }

    @PostMapping("/{accountId}/activate")
    public ApiResponse<AccountStatusView> activate(
            @PathVariable long accountId,
            @Valid @RequestBody(required = false) UpdateAccountStatusRequest body,
            HttpServletRequest request) {
        AccountStatusView view = accountAdministrationService.activate(
                accountId,
                body == null ? null : body.reason(),
                authLogics.admin().getLoginIdAsLong(),
                RequestIdFilter.current(request));
        return ApiResponse.success(view, RequestIdFilter.current(request));
    }
}
