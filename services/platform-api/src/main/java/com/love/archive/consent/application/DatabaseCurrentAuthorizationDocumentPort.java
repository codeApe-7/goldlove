package com.love.archive.consent.application;

import com.love.archive.payment.application.CurrentAuthorizationDocumentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class DatabaseCurrentAuthorizationDocumentPort implements CurrentAuthorizationDocumentPort {

    private static final String DOCUMENT_CODE = "PAID_PROFILE_LIVE_CONTENT";

    private final AuthorizationDocumentQuery authorizationDocumentQuery;

    @Override
    public long requireActiveDocumentId(String version) {
        return authorizationDocumentQuery.requireActive(DOCUMENT_CODE, version).id();
    }
}
