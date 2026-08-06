package com.love.archive.identity.web;

import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.identity.application.GuestProvisioningService;
import com.love.archive.identity.security.AuthLogics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AdminGuestAccountController {

    private final GuestProvisioningService provisioningService;
    private final AuthLogics authLogics;

    public AdminGuestAccountController(GuestProvisioningService provisioningService, AuthLogics authLogics) {
        this.provisioningService = provisioningService;
        this.authLogics = authLogics;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProvisionedGuestView>> create(
            @Valid @RequestBody CreateGuestAccountRequest body,
            HttpServletRequest request) {
        ProvisionedGuestView account = provisioningService.provision(
                authLogics.admin().getLoginIdAsLong(),
                body.phone(),
                body.paymentReference(),
                body.amountMinor(),
                body.paidAt(),
                body.note(),
                RequestIdFilter.current(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(account, RequestIdFilter.current(request)));
    }
}
