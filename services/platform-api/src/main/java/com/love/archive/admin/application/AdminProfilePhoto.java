package com.love.archive.admin.application;

/** previewUrl 是短时签名地址，从不落库。 */
public record AdminProfilePhoto(
        long id,
        String category,
        int sortOrder,
        String previewUrl) {
}
