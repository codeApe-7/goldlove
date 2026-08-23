package com.love.archive.course.web;

import com.love.archive.course.domain.CourseCollectionStatus;
import jakarta.validation.constraints.Size;

/**
 * 局部更新：字段为 {@code null} 表示「这一项不改」。
 *
 * <p>所以名称这里不能加 {@code @NotBlank}——那会让「只改排序」的请求也必须带上名称。
 * 「传了但是空白」由服务层拒绝。</p>
 */
public record UpdateCourseCollectionRequest(
        @Size(max = 64, message = "合集名称不能超过 64 个字")
        String name,

        @Size(max = 255, message = "合集说明不能超过 255 个字")
        String description,

        Integer sortOrder,

        CourseCollectionStatus status) {
}
