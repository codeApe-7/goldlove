package com.love.archive.admin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 200) String password) {
}
