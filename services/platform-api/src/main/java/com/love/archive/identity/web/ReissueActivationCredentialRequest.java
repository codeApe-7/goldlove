package com.love.archive.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReissueActivationCredentialRequest(
        @NotBlank @Size(max = 32) String phone) {
}
