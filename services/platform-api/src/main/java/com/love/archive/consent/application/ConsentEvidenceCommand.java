package com.love.archive.consent.application;

public record ConsentEvidenceCommand(
        String authorizationDocumentVersion,
        boolean accepted,
        String sourcePage,
        String clientIp,
        String userAgent,
        String sessionReference) {
}
