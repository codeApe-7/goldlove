package com.love.archive.guest.application;

import java.time.OffsetDateTime;

public record ProfilePhotoView(
        long id,
        String category,
        String objectKey,
        int sortOrder,
        String downloadUrl,
        OffsetDateTime createdAt) {
}
