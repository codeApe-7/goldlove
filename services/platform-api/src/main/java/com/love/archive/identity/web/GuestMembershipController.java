package com.love.archive.identity.web;

import com.love.archive.common.security.GuestAccountIdentity;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.identity.application.MembershipService;
import com.love.archive.identity.application.MembershipView;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 访客查询本人会员等级与累计付费额度。SVIP 的具体权益本期不实现。 */
@RestController
@RequestMapping("/api/v1/guest/membership")
@RequiredArgsConstructor
public class GuestMembershipController {

    private final MembershipService membershipService;
    private final GuestAccountIdentity guestAccountIdentity;

    @GetMapping
    public ApiResponse<MembershipView> current(HttpServletRequest request) {
        MembershipView membership = membershipService.current(
                guestAccountIdentity.currentGuestAccountId());
        return ApiResponse.success(membership, RequestIdFilter.current(request));
    }
}
