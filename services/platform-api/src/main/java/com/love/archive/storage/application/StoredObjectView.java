package com.love.archive.storage.application;

public record StoredObjectView(
        String objectKey,
        String bucket,
        long sizeBytes,
        String contentType,
        String sha256) {
}
