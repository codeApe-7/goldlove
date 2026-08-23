package com.love.archive.course.application;

/**
 * 上传 {@code .md} 文件的结果。
 *
 * <p>正文**不在这一步落库**：这个接口只把文件读成文本回给编辑器，
 * 管理员看过、可能还改过之后，才随保存课程一起入库。</p>
 */
public record MarkdownUploadView(String content, long sizeBytes) {
}
