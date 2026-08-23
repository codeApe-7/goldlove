package com.love.archive.course.application;

import java.time.OffsetDateTime;

/**
 * 访客课程列表的一行。
 *
 * <p><b>这个类型刻意不含 {@code contentMarkdown} 与视频播放地址。</b>
 * 列表对免费用户开放，正文与播放地址是付费内容——把字段留在类型里再靠代码记得置空，
 * 迟早会有人漏掉一条分支。字段不存在，就不可能漏。</p>
 *
 * <p>{@code locked} 只是给界面画锁标用的提示。真正的门禁在详情接口上，
 * 前端把它改成 false 也拿不到正文。</p>
 */
public record GuestCourseListItem(
        long id,
        long collectionId,
        String collectionName,
        String title,
        String subtitle,
        String summary,
        String contentType,
        String authorName,
        Integer videoDurationSeconds,
        String coverPreviewUrl,
        OffsetDateTime publishedAt,
        boolean locked) {
}
