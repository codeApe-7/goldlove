package com.love.archive.course.application;

import java.time.OffsetDateTime;

/** 后台课程列表的一行。正文不在列表里——列表页不需要，传了只是浪费带宽。 */
public record AdminCourseListItem(
        long id,
        long collectionId,
        String collectionName,
        String title,
        String subtitle,
        String contentType,
        String status,
        String authorName,
        Integer videoDurationSeconds,
        int sortOrder,
        OffsetDateTime publishedAt,
        OffsetDateTime updatedAt,
        long version) {
}
