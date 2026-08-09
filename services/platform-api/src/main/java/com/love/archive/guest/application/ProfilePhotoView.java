package com.love.archive.guest.application;

import java.time.OffsetDateTime;

public record ProfilePhotoView(
        long id,
        String category,
        String sha256,
        long sizeBytes,
        String contentType,
        int width,
        int height,
        int sortOrder,
        String downloadUrl,
        OffsetDateTime createdAt) {
}
