package com.love.archive.consent.web;

import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.consent.application.AuthorizationDocumentQuery;
import com.love.archive.consent.application.AuthorizationDocumentView;
import com.love.archive.consent.application.ConsentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 授权书对外公开：注册页的勾选框要能点开全文，此时用户还没有账号。 */
@RestController
@RequiredArgsConstructor
public class AuthorizationDocumentController {

    private final AuthorizationDocumentQuery authorizationDocumentQuery;

    @GetMapping("/api/v1/public/authorization-documents/current")
    public ApiResponse<AuthorizationDocumentView> current(HttpServletRequest request) {
        AuthorizationDocumentView document =
                authorizationDocumentQuery.current(ConsentService.DOCUMENT_CODE);
        return ApiResponse.success(document, RequestIdFilter.current(request));
    }

    @GetMapping("/api/v1/public/authorization-documents/{version}")
    public ApiResponse<AuthorizationDocumentView> getVersion(
            @PathVariable String version,
            HttpServletRequest request) {
        AuthorizationDocumentView document =
                authorizationDocumentQuery.requireActive(ConsentService.DOCUMENT_CODE, version);
        return ApiResponse.success(document, RequestIdFilter.current(request));
    }
}
