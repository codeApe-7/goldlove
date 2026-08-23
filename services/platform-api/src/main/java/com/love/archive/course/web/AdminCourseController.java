package com.love.archive.course.web;

import com.love.archive.common.security.AdminIdentity;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.PageView;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.course.application.AdminCourseDetail;
import com.love.archive.course.application.AdminCourseListItem;
import com.love.archive.course.application.CourseAdminService;
import com.love.archive.course.application.SaveCourseCommand;
import com.love.archive.course.domain.CourseContentType;
import com.love.archive.course.domain.CourseStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台的课程增删改查。类型与状态用枚举收参——绑不上的值 Spring 直接 400。 */
@RestController
@RequestMapping("/api/v1/admin/courses")
@RequiredArgsConstructor
public class AdminCourseController {

    private final CourseAdminService courseService;
    private final AdminIdentity adminIdentity;

    @GetMapping
    public ApiResponse<PageView<AdminCourseListItem>> list(
            @RequestParam(required = false) Long collectionId,
            @RequestParam(required = false) CourseContentType contentType,
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            HttpServletRequest request) {
        return ApiResponse.success(
                courseService.list(collectionId, contentType, status, keyword, page, size),
                RequestIdFilter.current(request));
    }

    @GetMapping("/{courseId}")
    public ApiResponse<AdminCourseDetail> detail(
            @PathVariable long courseId, HttpServletRequest request) {
        return ApiResponse.success(
                courseService.detail(courseId), RequestIdFilter.current(request));
    }

    @PostMapping
    public ApiResponse<AdminCourseDetail> create(
            @Valid @RequestBody SaveCourseRequest body, HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                courseService.create(toCommand(body), adminIdentity.currentAdminId(), requestId),
                requestId);
    }

    @PutMapping("/{courseId}")
    public ApiResponse<AdminCourseDetail> update(
            @PathVariable long courseId,
            @Valid @RequestBody SaveCourseRequest body,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                courseService.update(
                        courseId, toCommand(body), adminIdentity.currentAdminId(), requestId),
                requestId);
    }

    @PostMapping("/{courseId}/publish")
    public ApiResponse<AdminCourseDetail> publish(
            @PathVariable long courseId, HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                courseService.publish(courseId, adminIdentity.currentAdminId(), requestId),
                requestId);
    }

    @PostMapping("/{courseId}/archive")
    public ApiResponse<AdminCourseDetail> archive(
            @PathVariable long courseId, HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                courseService.archive(courseId, adminIdentity.currentAdminId(), requestId),
                requestId);
    }

    @DeleteMapping("/{courseId}")
    public ApiResponse<Void> delete(
            @PathVariable long courseId, HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        courseService.delete(courseId, adminIdentity.currentAdminId(), requestId);
        return ApiResponse.success(null, requestId);
    }

    private static SaveCourseCommand toCommand(SaveCourseRequest body) {
        return new SaveCourseCommand(
                body.collectionId(),
                body.title(),
                body.subtitle(),
                body.summary(),
                body.authorName(),
                body.contentType(),
                body.contentMarkdown(),
                body.coverObjectKey(),
                body.videoObjectKey(),
                body.videoDurationSeconds(),
                body.videoSizeBytes(),
                body.videoContentType(),
                body.sortOrder(),
                body.expectedVersion());
    }
}
