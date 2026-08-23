package com.love.archive.course.application;

import java.time.OffsetDateTime;

/**
 * 访客课程详情。只有付费会员能拿到这个对象——服务层在装配它之前就把门禁判掉了。
 *
 * <p>{@code videoUrl} 是 2 小时签名 URL，不落库。用 2 小时而不是照片那 15 分钟，
 * 是因为一节 15 分钟的课中途暂停一下链接就失效会很难看。</p>
 */
public record GuestCourseDetail(
        long id,
        long collectionId,
        String collectionName,
        String title,
        String subtitle,
        String summary,
        String contentType,
        String authorName,
        String contentMarkdown,
        String coverPreviewUrl,
        String videoUrl,
        Integer videoDurationSeconds,
        OffsetDateTime publishedAt) {
}
