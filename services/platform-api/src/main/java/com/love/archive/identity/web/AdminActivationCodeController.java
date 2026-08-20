package com.love.archive.identity.web;

import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.identity.application.ActivationCodeService;
import com.love.archive.identity.application.ActivationCodeView;
import com.love.archive.identity.application.GenerateActivationCodeCommand;
import com.love.archive.identity.security.AuthLogics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员生成与作废激活码。列表查询在 admin 模块（要 join 手机号与兑换人）。 */
@RestController
@RequestMapping("/api/v1/admin/activation-codes")
@RequiredArgsConstructor
public class AdminActivationCodeController {

    private final ActivationCodeService activationCodeService;
    private final AuthLogics authLogics;

    @PostMapping
    public ApiResponse<ActivationCodeView> generate(
            @Valid @RequestBody GenerateActivationCodeRequest body,
            HttpServletRequest request) {
        ActivationCodeView view = activationCodeService.generate(new GenerateActivationCodeCommand(
                body.boundPhone(),
                body.grantedTier(),
                body.note(),
                authLogics.admin().getLoginIdAsLong(),
                RequestIdFilter.current(request)));
        return ApiResponse.success(view, RequestIdFilter.current(request));
    }

    @PostMapping("/{codeId}/revoke")
    public ApiResponse<Void> revoke(@PathVariable long codeId, HttpServletRequest request) {
        activationCodeService.revoke(
                codeId, authLogics.admin().getLoginIdAsLong(), RequestIdFilter.current(request));
        return ApiResponse.success(null, RequestIdFilter.current(request));
    }
}
