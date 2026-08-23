package com.love.archive.course.application;

/**
 * 分块上传会话。{@code partSizeBytes} 由服务端下发，浏览器照它切片——
 * 让服务端定这个数，以后调整上限不用改前端。
 */
public record VideoUploadSessionView(
        String uploadId,
        String objectKey,
        int partSizeBytes) {
}
