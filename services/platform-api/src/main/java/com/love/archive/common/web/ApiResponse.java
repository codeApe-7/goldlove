package com.love.archive.common.web;

public record ApiResponse<T>(boolean success, String code, String message, T data, String requestId) {

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(true, "OK", "成功", data, requestId);
    }

    public static ApiResponse<Void> failure(String code, String message, String requestId) {
        return new ApiResponse<>(false, code, message, null, requestId);
    }
}
