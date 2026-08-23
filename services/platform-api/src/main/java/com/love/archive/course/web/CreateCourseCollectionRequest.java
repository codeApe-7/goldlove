package com.love.archive.course.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourseCollectionRequest(
        @NotBlank(message = "合集名称不能为空")
        @Size(max = 64, message = "合集名称不能超过 64 个字")
        String name,

        @Size(max = 255, message = "合集说明不能超过 255 个字")
        String description,

        Integer sortOrder) {
}
