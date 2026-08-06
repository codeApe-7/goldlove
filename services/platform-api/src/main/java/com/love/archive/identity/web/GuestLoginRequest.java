package com.love.archive.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestLoginRequest(
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Size(max = 200) String password) {
}
