package com.love.archive.course.application;

/**
 * 访客看到的合集。只报已发布的课程数——把草稿数报出去等于泄露还没上架的排期。
 */
public record GuestCourseCollectionView(
        long id,
        String name,
        String description,
        long courseCount) {
}
