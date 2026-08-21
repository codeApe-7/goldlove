package com.love.archive.identity.web;

import com.love.archive.common.security.GuestAccountIdentity;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.identity.application.ActivationCodeService;
import com.love.archive.identity.application.MembershipService;
import com.love.archive.identity.application.MembershipView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 访客查询本人会员等级，并可用激活码升级。SVIP 的具体权益本期不实现。 */
@RestController
@RequestMapping("/api/v1/guest/membership")
@RequiredArgsConstructor
public class GuestMembershipController {

    private final MembershipService membershipService;
    private final ActivationCodeService activationCodeService;
    private final GuestAccountIdentity guestAccountIdentity;

    @GetMapping
    public ApiResponse<MembershipView> current(HttpServletRequest request) {
        MembershipView membership = membershipService.current(
                guestAccountIdentity.currentGuestAccountId());
        return ApiResponse.success(membership, RequestIdFilter.current(request));
    }

    @PostMapping("/activation-codes")
    public ApiResponse<MembershipView> redeem(
            @Valid @RequestBody RedeemActivationCodeRequest body,
            HttpServletRequest request) {
        MembershipView membership = activationCodeService.redeem(
                guestAccountIdentity.currentGuestAccountId(),
                body.code(),
                RequestIdFilter.current(request));
        return ApiResponse.success(membership, RequestIdFilter.current(request));
    }
}
