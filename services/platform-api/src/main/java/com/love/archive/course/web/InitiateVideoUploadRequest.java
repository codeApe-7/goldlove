package com.love.archive.course.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InitiateVideoUploadRequest(
        @Size(max = 255, message = "文件名不能超过 255 个字")
        String filename,

        @NotBlank(message = "请提供视频类型")
        @Size(max = 128)
        String contentType,

        /** 浏览器声明的总大小，用于提前拒掉过大的文件；最终大小以 COS 实际存下的为准。 */
        @Positive(message = "视频大小必须为正数")
        Long totalBytes) {
}
