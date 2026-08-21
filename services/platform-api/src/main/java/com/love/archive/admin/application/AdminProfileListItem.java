package com.love.archive.admin.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminProfileListItem(
        long id,
        UUID profileNo,
        String phone,
        String membershipTier,
        String status,
        OffsetDateTime updatedAt) {
}
