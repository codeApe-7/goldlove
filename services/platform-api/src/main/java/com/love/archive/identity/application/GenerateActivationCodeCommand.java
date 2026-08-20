package com.love.archive.identity.application;

import com.love.archive.identity.domain.MembershipTier;

public record GenerateActivationCodeCommand(
        String boundPhone,
        MembershipTier grantedTier,
        String note,
        long adminId,
        String requestId) {
}
