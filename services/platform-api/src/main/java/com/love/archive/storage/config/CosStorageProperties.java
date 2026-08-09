package com.love.archive.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.cos")
public record CosStorageProperties(
        String secretId,
        String secretKey,
        String region,
        String bucket) {
}
