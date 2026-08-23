package com.love.archive.course.web;

import com.love.archive.course.domain.CourseContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 新建与更新课程共用的入参。
 *
 * <p>{@code videoDurationSeconds} 只有一个 {@code @Positive} 的数据合理性约束，
 * <b>刻意没有 5–15 分钟之类的区间校验</b>：产品口径是时长只登记、只展示，
 * 不参与任何校验、也不产生任何提示。</p>
 */
public record SaveCourseRequest(
        @NotNull(message = "请选择所属合集")
        Long collectionId,

        @NotBlank(message = "教材名称不能为空")
        @Size(max = 120, message = "教材名称不能超过 120 个字")
        String title,

        @Size(max = 200, message = "副标题不能超过 200 个字")
        String subtitle,

        @Size(max = 500, message = "简介不能超过 500 个字")
        String summary,

        @Size(max = 64, message = "讲师不能超过 64 个字")
        String authorName,

        @NotNull(message = "请选择课程类型")
        CourseContentType contentType,

        @Size(max = 1048576, message = "正文不能超过 1 MiB")
        String contentMarkdown,

        @Size(max = 1024)
        String coverObjectKey,

        @Size(max = 1024)
        String videoObjectKey,

        @Positive(message = "视频时长必须是正整数秒")
        Integer videoDurationSeconds,

        @Positive(message = "视频大小必须为正数")
        Long videoSizeBytes,

        @Size(max = 128)
        String videoContentType,

        Integer sortOrder,

        /** 更新时必传；新建时忽略。 */
        Long expectedVersion) {
}
