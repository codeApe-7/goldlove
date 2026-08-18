package com.love.archive.identity.application;

import com.love.archive.identity.domain.MembershipTier;

public record MembershipView(
        MembershipTier tier,
        long creditMinor,
        long svipThresholdMinor,
        long creditToNextTierMinor) {
}
