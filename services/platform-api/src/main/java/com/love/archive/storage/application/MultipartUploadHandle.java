package com.love.archive.storage.application;

/**
 * 一次分块上传会话的句柄。
 *
 * <p>{@code uploadId} 由 COS 生成，后续每一块与最终的 complete 都要带上它。
 * 它会随响应回到浏览器，所以调用方必须自己记住「这个会话是谁开的」——
 * 本模块只负责和 COS 说话，不做归属校验。</p>
 */
public record MultipartUploadHandle(String uploadId, String objectKey) {
}
