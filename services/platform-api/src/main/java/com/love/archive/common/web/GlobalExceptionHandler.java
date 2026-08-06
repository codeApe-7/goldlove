package com.love.archive.common.web;

import cn.dev33.satoken.exception.NotLoginException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status())
                .body(ApiResponse.failure(exception.code(), exception.getMessage(), RequestIdFilter.current(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                "VALIDATION_FAILED", "请求参数不正确", RequestIdFilter.current(request)));
    }

    @ExceptionHandler(NotLoginException.class)
    ResponseEntity<ApiResponse<Void>> handleNotLoggedIn(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.failure(
                "AUTH_NOT_LOGGED_IN", "请先登录", RequestIdFilter.current(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        LOGGER.error("Unhandled request failure, requestId={}", requestId, exception);
        return ResponseEntity.internalServerError().body(ApiResponse.failure(
                "INTERNAL_ERROR", "服务暂时不可用", requestId));
    }
}
