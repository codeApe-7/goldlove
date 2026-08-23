package com.love.archive.course.web;

import com.love.archive.common.security.GuestAccountIdentity;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.PageView;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.course.application.GuestCourseCollectionView;
import com.love.archive.course.application.GuestCourseDetail;
import com.love.archive.course.application.GuestCourseListItem;
import com.love.archive.course.application.GuestCourseService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访客端课程。
 *
 * <p>门禁是「列表可见、内容加锁」：合集与列表对所有登录访客开放，
 * 详情要付费会员。两者在同一个前缀下，所以门禁在 service 层判，
 * 不做成按路径匹配的拦截器。</p>
 */
@RestController
@RequestMapping("/api/v1/guest/courses")
@RequiredArgsConstructor
public class GuestCourseController {

    private final GuestCourseService courseService;
    private final GuestAccountIdentity guestIdentity;

    @GetMapping("/collections")
    public ApiResponse<List<GuestCourseCollectionView>> collections(HttpServletRequest request) {
        return ApiResponse.success(
                courseService.listCollections(), RequestIdFilter.current(request));
    }

    @GetMapping
    public ApiResponse<PageView<GuestCourseListItem>> list(
            @RequestParam(required = false) Long collectionId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            HttpServletRequest request) {
        return ApiResponse.success(
                courseService.list(
                        guestIdentity.currentGuestAccountId(), collectionId, page, size),
                RequestIdFilter.current(request));
    }

    /** 付费会员专属；免费用户拿到 403 {@code COURSE_VIP_REQUIRED}。 */
    @GetMapping("/{courseId}")
    public ApiResponse<GuestCourseDetail> detail(
            @PathVariable long courseId, HttpServletRequest request) {
        return ApiResponse.success(
                courseService.detail(guestIdentity.currentGuestAccountId(), courseId),
                RequestIdFilter.current(request));
    }
}
