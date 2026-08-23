package com.love.archive.course.domain;

/**
 * 课程内容形态。
 *
 * <p>{@link #ARTICLE} 与 {@link #TEXT} 的正文都是 markdown 存在库里，区别是前者带插图；
 * {@link #VIDEO} 的正文可选，真正的内容是 COS 上的视频对象。
 * 库里的 {@code ck_course_content} 会照这个语义把「没有内容的课」拦住。</p>
 */
public enum CourseContentType {

    /** 图文：markdown 正文 + 插图。 */
    ARTICLE,

    /** 视频：COS 对象；正文可选，用作简介。 */
    VIDEO,

    /** 纯文本：markdown 正文，不含插图。 */
    TEXT
}
