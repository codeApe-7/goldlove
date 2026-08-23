package com.love.archive.course.application;

/**
 * 分块合并完成后的视频对象。
 *
 * <p>大小与类型以 COS 实际存下的为准，不用浏览器声明的数字——
 * 这两个值会被写进课程记录，将来是展示给用户看的。</p>
 *
 * <p>{@code previewUrl} 是短时签名地址，让管理员**在保存课程之前**就能回放确认
 * 传上去的是不是那个文件、清晰度对不对。没有它的话，唯一的验证方式是先保存再重新打开
 * 编辑抽屉，传错了就得连课程一起返工。不落库，过期重新拉课程详情即可。</p>
 */
public record VideoUploadResultView(
        String objectKey,
        long sizeBytes,
        String contentType,
        String previewUrl) {
}
