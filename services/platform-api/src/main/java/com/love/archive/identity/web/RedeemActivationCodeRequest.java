package com.love.archive.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedeemActivationCodeRequest(@NotBlank @Size(max = 32) String code) {
}
