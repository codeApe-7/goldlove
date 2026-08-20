package com.love.archive.identity.application;

public record SelfRegistrationCommand(
        String phone,
        String password,
        String confirmPassword,
        boolean acceptedAuthorization,
        String authorizationDocumentVersion,
        String clientIp,
        String userAgent,
        String requestId) {
}
