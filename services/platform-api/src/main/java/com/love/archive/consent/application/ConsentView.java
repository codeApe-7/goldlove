package com.love.archive.consent.application;

import java.time.OffsetDateTime;

public record ConsentView(
        long id,
        long authorizationDocumentId,
        String authorizationDocumentVersion,
        OffsetDateTime acceptedAt,
        OffsetDateTime effectiveAt,
        OffsetDateTime expiresAt,
        String sourcePage) {
}
