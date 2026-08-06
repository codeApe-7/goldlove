package com.love.archive.identity.web;

import com.love.archive.identity.domain.AccountStatus;
import java.time.OffsetDateTime;

public record ProvisionedGuestView(
        Long accountId,
        AccountStatus status,
        String initialCredential,
        OffsetDateTime expiresAt) {
}
