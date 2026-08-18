package com.love.archive.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OnlineRegistrationRequest(
        @NotBlank @Size(max = 128) String registrationToken,
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Size(max = 128) String password) {
}
