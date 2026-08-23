package com.love.archive.course.domain;

/**
 * 合集状态。
 *
 * <p>没有「已删除」：合集下面挂着课程，删行会留孤儿，所以库里也没给
 * {@code archive_app} DELETE 权限。下线走 {@link #HIDDEN}。</p>
 */
public enum CourseCollectionStatus {

    ACTIVE,

    HIDDEN
}
