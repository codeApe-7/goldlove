package com.love.archive.identity.web;

import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.domain.MembershipTier;

public record GuestSessionView(
        Long accountId,
        AccountStatus status,
        MembershipTier membershipTier,
        String accessToken,
        long expiresIn) {
}
