package com.love.archive.consent.application;

import java.time.OffsetDateTime;

public record AuthorizationDocumentView(
        long id,
        String documentCode,
        String version,
        String title,
        String content,
        String contentSha256,
        OffsetDateTime effectiveAt) {
}
