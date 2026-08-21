package com.love.archive.identity.web;

import com.love.archive.identity.domain.MembershipTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateActivationCodeRequest(
        @NotBlank @Size(max = 32) String boundPhone,
        MembershipTier grantedTier,
        @Size(max = 200) String note) {
}
