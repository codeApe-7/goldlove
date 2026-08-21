package com.love.archive.identity.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SelfRegistrationRequest(
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Size(max = 128) String password,
        @NotBlank @Size(max = 128) String confirmPassword,
        @AssertTrue(message = "必须阅读并同意授权书") boolean acceptedAuthorization,
        @Size(max = 32) String authorizationDocumentVersion) {
}
