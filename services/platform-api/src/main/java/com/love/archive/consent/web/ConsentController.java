package com.love.archive.consent.web;

import cn.dev33.satoken.stp.StpLogic;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.consent.application.ConsentEvidenceCommand;
import com.love.archive.consent.application.ConsentService;
import com.love.archive.consent.application.ConsentView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guest/consents")
public class ConsentController {

    private final ConsentService consentService;
    private final ClientEvidenceResolver clientEvidenceResolver;
    private final StpLogic guestStpLogic;

    public ConsentController(
            ConsentService consentService,
            ClientEvidenceResolver clientEvidenceResolver,
            @Qualifier("guestStpLogic") StpLogic guestStpLogic) {
        this.consentService = consentService;
        this.clientEvidenceResolver = clientEvidenceResolver;
        this.guestStpLogic = guestStpLogic;
    }

    @PostMapping
    public ApiResponse<ConsentView> accept(
            @Valid @RequestBody AcceptConsentRequest body,
            HttpServletRequest request) {
        long accountId = guestStpLogic.getLoginIdAsLong();
        ConsentView consent = consentService.accept(accountId, new ConsentEvidenceCommand(
                body.authorizationDocumentVersion(),
                body.accepted(),
                body.sourcePage(),
                clientEvidenceResolver.clientIp(request),
                clientEvidenceResolver.userAgent(request),
                guestStpLogic.getTokenValue()));
        return ApiResponse.success(consent, RequestIdFilter.current(request));
    }

    @GetMapping("/current")
    public ApiResponse<CurrentConsentView> current(HttpServletRequest request) {
        CurrentConsentView view = consentService.current(guestStpLogic.getLoginIdAsLong())
                .map(CurrentConsentView::present)
                .orElseGet(CurrentConsentView::absent);
        return ApiResponse.success(view, RequestIdFilter.current(request));
    }
}
