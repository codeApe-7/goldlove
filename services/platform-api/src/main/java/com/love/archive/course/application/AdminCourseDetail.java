package com.love.archive.course.application;

import java.time.OffsetDateTime;

/**
 * 后台课程详情。
 *
 * <p>预览地址是短时签名 URL，不落库：管理员打开编辑抽屉时才签，过期重新拉详情即可。</p>
 */
public record AdminCourseDetail(
        long id,
        long collectionId,
        String collectionName,
        String title,
        String subtitle,
        String summary,
        String authorName,
        String contentType,
        String status,
        String contentMarkdown,
        String coverObjectKey,
        String coverPreviewUrl,
        String videoObjectKey,
        String videoPreviewUrl,
        Integer videoDurationSeconds,
        Long videoSizeBytes,
        String videoContentType,
        int sortOrder,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {
}
