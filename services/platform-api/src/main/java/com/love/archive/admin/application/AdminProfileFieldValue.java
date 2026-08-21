package com.love.archive.admin.application;

public record AdminProfileFieldValue(
        String fieldCode,
        String label,
        String dataType,
        String value) {
}
