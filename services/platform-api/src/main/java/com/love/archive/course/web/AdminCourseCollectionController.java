package com.love.archive.course.web;

import com.love.archive.common.security.AdminIdentity;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.course.application.AdminCourseCollectionView;
import com.love.archive.course.application.CourseCollectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台维护课程目录（表与接口路径仍叫 collection，只有文案改成「目录」）。
 *
 * <p>控制器放在 course 模块——合集是这个模块的数据，与激活码在 identity、
 * 字段定义在 guest 是同一套做法：写操作在数据所属模块，只读的跨表列表才在 admin。</p>
 *
 * <p>**没有删除端点**：合集下面挂着课程，删了会留孤儿，库里也没给运行账号
 * DELETE 权限。下线走 {@code PATCH} 把 status 改成 HIDDEN。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/course-collections")
@RequiredArgsConstructor
public class AdminCourseCollectionController {

    private final CourseCollectionService collectionService;
    private final AdminIdentity adminIdentity;

    @GetMapping
    public ApiResponse<List<AdminCourseCollectionView>> list(HttpServletRequest request) {
        return ApiResponse.success(
                collectionService.listForAdmin(), RequestIdFilter.current(request));
    }

    @PostMapping
    public ApiResponse<AdminCourseCollectionView> create(
            @Valid @RequestBody CreateCourseCollectionRequest body,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                collectionService.create(
                        body.name(),
                        body.description(),
                        body.sortOrder(),
                        adminIdentity.currentAdminId(),
                        requestId),
                requestId);
    }

    @PatchMapping("/{collectionId}")
    public ApiResponse<AdminCourseCollectionView> update(
            @PathVariable long collectionId,
            @Valid @RequestBody UpdateCourseCollectionRequest body,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                collectionService.update(
                        collectionId,
                        body.name(),
                        body.description(),
                        body.sortOrder(),
                        body.status(),
                        adminIdentity.currentAdminId(),
                        requestId),
                requestId);
    }
}
