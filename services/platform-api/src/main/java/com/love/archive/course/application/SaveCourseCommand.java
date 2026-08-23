package com.love.archive.course.application;

import com.love.archive.course.domain.CourseContentType;

/**
 * 新建或更新课程的入参。
 *
 * <p>{@code expectedVersion} 只在更新时有值：新建不带，带了也没有意义。
 * {@code videoDurationSeconds} 只登记，不参与任何校验。</p>
 */
public record SaveCourseCommand(
        long collectionId,
        String title,
        String subtitle,
        String summary,
        String authorName,
        CourseContentType contentType,
        String contentMarkdown,
        String coverObjectKey,
        String videoObjectKey,
        Integer videoDurationSeconds,
        Long videoSizeBytes,
        String videoContentType,
        Integer sortOrder,
        Long expectedVersion) {
}
