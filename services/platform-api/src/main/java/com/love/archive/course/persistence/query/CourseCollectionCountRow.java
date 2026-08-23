package com.love.archive.course.persistence.query;

import lombok.Getter;
import lombok.Setter;

/**
 * 每个合集下的课程数量，一条 SQL 用 COUNT FILTER 一次取回。
 *
 * <p>分两个数：后台要看「一共几节」，H5 只该看到已发布的那几节。</p>
 */
@Getter
@Setter
public class CourseCollectionCountRow {

    private Long collectionId;
    private Long courseCount;
    private Long publishedCourseCount;
}
