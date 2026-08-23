package com.love.archive.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.love.archive.common.security.GuestMembershipAccess;
import com.love.archive.common.web.PageView;
import com.love.archive.course.domain.CourseCollectionStatus;
import com.love.archive.course.domain.CourseStatus;
import com.love.archive.course.persistence.CourseCollectionEntity;
import com.love.archive.course.persistence.CourseCollectionMapper;
import com.love.archive.course.persistence.CourseEntity;
import com.love.archive.course.persistence.CourseMapper;
import com.love.archive.storage.application.ObjectStorageService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 访客端的课程读取，以及付费门禁。
 *
 * <p>口径是「列表可见、内容加锁」：
 * <ul>
 *   <li>合集与课程列表对所有登录访客开放，返回的 {@link GuestCourseListItem}
 *       <b>类型上就不含</b>正文与播放地址；</li>
 *   <li>详情需要付费会员，否则 403 {@code COURSE_VIP_REQUIRED}。</li>
 * </ul>
 * 列表项上的 {@code locked} 只是给界面画锁标用的提示，不是门禁本身——
 * 前端把它改成 false 也拿不到正文，因为正文根本不在那个响应里。</p>
 *
 * <p>门禁放在这一层而不是拦截器：同一个 {@code /api/v1/guest/courses} 前缀下
 * 列表要放行、详情要拦，按路径判断的拦截器会随着端点增加越来越脆。</p>
 */
@Service
@RequiredArgsConstructor
public class GuestCourseService {

    /**
     * 视频播放地址的有效期。
     *
     * <p>用 2 小时而不是照片那 15 分钟：一节 15 分钟的课，中途接个电话回来
     * 链接就失效了会很难看。代价是这个 URL 在 2 小时内被转发出去也能播——
     * 私有桶签名地址防不住转发，这是已知取舍，真要防泄露得靠水印加账号追责。</p>
     */
    private static final Duration VIDEO_TTL = Duration.ofHours(2);

    /** 封面只在列表与详情上露一眼，短一点就够。 */
    private static final Duration COVER_TTL = Duration.ofMinutes(30);

    private final CourseMapper courseMapper;
    private final CourseCollectionMapper collectionMapper;
    private final CourseCollectionService collectionService;
    private final ObjectStorageService storageService;
    private final GuestMembershipAccess membershipAccess;

    @Transactional(readOnly = true)
    public List<GuestCourseCollectionView> listCollections() {
        return collectionService.listForGuest();
    }

    /**
     * 已发布课程的分页列表。免费用户也能调，拿到的是元数据。
     */
    @Transactional(readOnly = true)
    public PageView<GuestCourseListItem> list(
            long accountId, Long collectionId, long requestedPage, long requestedSize) {
        long pageNumber = Math.max(1, requestedPage);
        long pageSize = Math.min(50, Math.max(1, requestedSize));
        boolean locked = !membershipAccess.isPaidMember(accountId);

        List<Long> visibleCollectionIds = activeCollectionIds();
        if (visibleCollectionIds.isEmpty()) {
            return new PageView<>(List.of(), pageNumber, pageSize, 0L);
        }
        if (collectionId != null && !visibleCollectionIds.contains(collectionId)) {
            // 指定了一个被隐藏（或不存在）的合集：给空列表而不是报错——
            // 合集刚被后台下线时，H5 上还留着旧 tab 的人不该看到一个错误弹窗。
            return new PageView<>(List.of(), pageNumber, pageSize, 0L);
        }

        Page<CourseEntity> page = courseMapper.selectPage(
                Page.of(pageNumber, pageSize),
                Wrappers.<CourseEntity>lambdaQuery()
                        .eq(CourseEntity::getStatus, CourseStatus.PUBLISHED)
                        .eq(collectionId != null, CourseEntity::getCollectionId, collectionId)
                        .in(collectionId == null, CourseEntity::getCollectionId, visibleCollectionIds)
                        .orderByAsc(CourseEntity::getSortOrder)
                        .orderByDesc(CourseEntity::getPublishedAt)
                        .orderByDesc(CourseEntity::getId));

        Map<Long, String> collectionNames = collectionService.namesById();
        return new PageView<>(
                page.getRecords().stream()
                        .map(course -> toListItem(course, collectionNames, locked))
                        .toList(),
                pageNumber,
                pageSize,
                page.getTotal());
    }

    /**
     * 课程详情。**付费会员专属**。
     *
     * <p>门禁先判、再查库：免费用户连「这个 id 存不存在」都探测不到。</p>
     */
    @Transactional(readOnly = true)
    public GuestCourseDetail detail(long accountId, long courseId) {
        if (!membershipAccess.isPaidMember(accountId)) {
            throw CourseErrors.vipRequired();
        }

        CourseEntity course = courseMapper.selectById(courseId);
        if (course == null || course.getStatus() != CourseStatus.PUBLISHED) {
            throw CourseErrors.courseNotFound();
        }
        if (!activeCollectionIds().contains(course.getCollectionId())) {
            // 合集被下线，等于这节课也不该露面。
            throw CourseErrors.courseNotFound();
        }

        return new GuestCourseDetail(
                course.getId(),
                course.getCollectionId(),
                collectionService.namesById().get(course.getCollectionId()),
                course.getTitle(),
                course.getSubtitle(),
                course.getSummary(),
                course.getContentType().name(),
                course.getAuthorName(),
                course.getContentMarkdown(),
                sign(course.getCoverObjectKey(), COVER_TTL),
                sign(course.getVideoObjectKey(), VIDEO_TTL),
                course.getVideoDurationSeconds(),
                course.getPublishedAt());
    }

    private List<Long> activeCollectionIds() {
        return collectionMapper
                .selectList(Wrappers.<CourseCollectionEntity>lambdaQuery()
                        .select(CourseCollectionEntity::getId)
                        .eq(CourseCollectionEntity::getStatus, CourseCollectionStatus.ACTIVE))
                .stream()
                .map(CourseCollectionEntity::getId)
                .toList();
    }

    private GuestCourseListItem toListItem(
            CourseEntity course, Map<Long, String> collectionNames, boolean locked) {
        return new GuestCourseListItem(
                course.getId(),
                course.getCollectionId(),
                collectionNames.get(course.getCollectionId()),
                course.getTitle(),
                course.getSubtitle(),
                course.getSummary(),
                course.getContentType().name(),
                course.getAuthorName(),
                course.getVideoDurationSeconds(),
                sign(course.getCoverObjectKey(), COVER_TTL),
                course.getPublishedAt(),
                locked);
    }

    private String sign(String objectKey, Duration ttl) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        return storageService.signDownloadUrl(objectKey, ttl);
    }
}
