package com.love.archive.course.application;

/** 上传插图或封面的结果。预览地址是短时签名 URL，不落库。 */
public record CourseImageUploadView(
        String objectKey,
        String previewUrl,
        long sizeBytes,
        String contentType) {
}
