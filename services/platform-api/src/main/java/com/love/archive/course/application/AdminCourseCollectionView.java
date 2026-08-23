package com.love.archive.course.application;

import java.time.OffsetDateTime;

/**
 * 后台看到的合集。
 *
 * <p>两个课程数都给：运营要判断「这个合集能不能下线」看的是总数，
 * 而「H5 上现在有几节可看」看的是已发布数。</p>
 */
public record AdminCourseCollectionView(
        long id,
        String name,
        String description,
        int sortOrder,
        String status,
        long courseCount,
        long publishedCourseCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
