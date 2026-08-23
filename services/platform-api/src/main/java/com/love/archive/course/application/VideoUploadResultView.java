package com.love.archive.course.application;

/**
 * 分块合并完成后的视频对象。
 *
 * <p>大小与类型以 COS 实际存下的为准，不用浏览器声明的数字——
 * 这两个值会被写进课程记录，将来是展示给用户看的。</p>
 */
public record VideoUploadResultView(
        String objectKey,
        long sizeBytes,
        String contentType) {
}
