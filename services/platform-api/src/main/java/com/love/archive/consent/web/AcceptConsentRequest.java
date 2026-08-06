package com.love.archive.consent.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptConsentRequest(
        @NotBlank @Size(max = 32) String authorizationDocumentVersion,
        @AssertTrue boolean accepted,
        @NotBlank @Size(max = 64) String sourcePage) {
}
