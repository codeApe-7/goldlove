package com.love.archive.course.domain;

/**
 * 课程上下架状态。
 *
 * <p>只有 {@link #PUBLISHED} 对访客可见。{@link #ARCHIVED} 是下架而非删除——
 * 已经上过架的课直接删掉，运营就查不到「这节课当时讲了什么」。</p>
 */
public enum CourseStatus {

    DRAFT,

    PUBLISHED,

    ARCHIVED
}
