package com.love.archive.consent.web;

import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.common.security.GuestAccountIdentity;
import com.love.archive.consent.application.AuthorizationDocumentQuery;
import com.love.archive.consent.application.AuthorizationDocumentView;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthorizationDocumentController {

    private static final String DOCUMENT_CODE = "PAID_PROFILE_LIVE_CONTENT";

    private final AuthorizationDocumentQuery authorizationDocumentQuery;
    private final GuestAccountIdentity guestAccountIdentity;

    @GetMapping("/api/v1/public/authorization-documents/current")
    public ApiResponse<AuthorizationDocumentView> current(HttpServletRequest request) {
        AuthorizationDocumentView document = authorizationDocumentQuery.current(DOCUMENT_CODE);
        return ApiResponse.success(document, RequestIdFilter.current(request));
    }

    @GetMapping("/api/v1/guest/authorization-documents/{version}")
    public ApiResponse<AuthorizationDocumentView> getVersion(
            @PathVariable String version,
            HttpServletRequest request) {
        AuthorizationDocumentView document = authorizationDocumentQuery.requireVisibleToGuest(
                guestAccountIdentity.currentGuestAccountId(), DOCUMENT_CODE, version);
        return ApiResponse.success(document, RequestIdFilter.current(request));
    }
}
