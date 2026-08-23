package com.love.archive.storage.application;

/**
 * 已上传分块的引用：块号 + COS 返回的 ETag。
 *
 * <p>complete 时要把全部分块按块号升序交回 COS，少一块或顺序错了对象就拼不出来。
 * ETag 由 COS 生成，服务端不自己算——分块上传时服务端从没同时握有整个文件。</p>
 */
public record PartRef(int partNumber, String etag) {
}
