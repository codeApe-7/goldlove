package com.love.archive.course.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record CompleteVideoUploadRequest(
        @NotEmpty(message = "分块清单不能为空")
        @Valid
        List<VideoPartInput> parts) {

    public record VideoPartInput(
            @Positive(message = "分块号从 1 开始")
            Integer partNumber,

            @NotBlank(message = "分块 ETag 不能为空")
            String etag) {
    }
}
