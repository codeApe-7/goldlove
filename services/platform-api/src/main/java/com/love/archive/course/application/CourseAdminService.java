package com.love.archive.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.web.PageView;
import com.love.archive.course.domain.CourseContentType;
import com.love.archive.course.domain.CourseStatus;
import com.love.archive.course.persistence.CourseCollectionEntity;
import com.love.archive.course.persistence.CourseEntity;
import com.love.archive.course.persistence.CourseMapper;
import com.love.archive.storage.application.ObjectStorageService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 后台的课程读写。
 *
 * <p>正文存库，封面与视频存 COS。预览地址一律现签现用、不落库。</p>
 */
@Service
@RequiredArgsConstructor
public class CourseAdminService {

    /** 后台预览用的签名有效期。管理员看一眼就够，不需要跟播放路径一样长。 */
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(30);

    /** 与 {@code SaveCourseRequest} 上的 {@code @Size} 保持一致。 */
    public static final int MAX_MARKDOWN_CHARS = 1024 * 1024;

    private final CourseMapper courseMapper;
    private final CourseCollectionService collectionService;
    private final ObjectStorageService storageService;
    private final AuditTrail auditTrail;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageView<AdminCourseListItem> list(
            Long collectionId,
            CourseContentType contentType,
            CourseStatus status,
            String keyword,
            long requestedPage,
            long requestedSize) {
        long pageNumber = Math.max(1, requestedPage);
        long pageSize = Math.min(100, Math.max(1, requestedSize));
        String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

        Page<CourseEntity> page = courseMapper.selectPage(
                Page.of(pageNumber, pageSize),
                Wrappers.<CourseEntity>lambdaQuery()
                        .eq(collectionId != null, CourseEntity::getCollectionId, collectionId)
                        .eq(contentType != null, CourseEntity::getContentType, contentType)
                        .eq(status != null, CourseEntity::getStatus, status)
                        // 关键词只搜标题与副标题：正文可能上百 KB，全表 LIKE 会把库拖垮。
                        .and(trimmedKeyword != null, wrapper -> wrapper
                                .like(CourseEntity::getTitle, trimmedKeyword)
                                .or()
                                .like(CourseEntity::getSubtitle, trimmedKeyword))
                        .orderByAsc(CourseEntity::getSortOrder)
                        .orderByDesc(CourseEntity::getId));

        Map<Long, String> collectionNames = collectionService.namesById();
        return new PageView<>(
                page.getRecords().stream()
                        .map(course -> toListItem(course, collectionNames))
                        .toList(),
                pageNumber,
                pageSize,
                page.getTotal());
    }

    @Transactional(readOnly = true)
    public AdminCourseDetail detail(long courseId) {
        CourseEntity course = requireCourse(courseId);
        return toDetail(course, collectionService.namesById());
    }

    @Transactional
    public AdminCourseDetail create(SaveCourseCommand command, long adminId, String requestId) {
        CourseCollectionEntity collection =
                collectionService.requireCollection(command.collectionId());
        validateContent(command);

        OffsetDateTime now = OffsetDateTime.now();
        CourseEntity course = new CourseEntity();
        applyCommand(course, command);
        course.setStatus(CourseStatus.DRAFT);
        course.setCreatedByAdminId(adminId);
        course.setUpdatedByAdminId(adminId);
        course.setVersion(0L);
        course.setCreatedAt(now);
        course.setUpdatedAt(now);
        courseMapper.insert(course);

        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.ADMIN,
                adminId,
                "COURSE_CREATED",
                "COURSE",
                course.getId(),
                requestId,
                metadata(Map.of(
                        "title", course.getTitle(),
                        "contentType", course.getContentType().name(),
                        "collectionId", collection.getId())),
                now));
        return toDetail(course, collectionService.namesById());
    }

    @Transactional
    public AdminCourseDetail update(
            long courseId, SaveCourseCommand command, long adminId, String requestId) {
        CourseEntity existing = requireCourse(courseId);
        collectionService.requireCollection(command.collectionId());
        validateContent(command);
        if (command.expectedVersion() == null) {
            throw CourseErrors.versionConflict();
        }

        OffsetDateTime now = OffsetDateTime.now();
        CourseEntity update = new CourseEntity();
        update.setId(courseId);
        applyCommand(update, command);
        update.setUpdatedByAdminId(adminId);
        update.setUpdatedAt(now);
        // @Version 让 MyBatis-Plus 把 version 加进 WHERE 并自增；不匹配就影响 0 行。
        update.setVersion(command.expectedVersion());
        if (courseMapper.updateById(update) != 1) {
            throw CourseErrors.versionConflict();
        }

        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.ADMIN,
                adminId,
                "COURSE_UPDATED",
                "COURSE",
                courseId,
                requestId,
                metadata(changes(existing, command)),
                now));
        return detail(courseId);
    }

    @Transactional
    public AdminCourseDetail publish(long courseId, long adminId, String requestId) {
        CourseEntity course = requireCourse(courseId);
        OffsetDateTime now = OffsetDateTime.now();
        // 首次上架才记时间，重新上架保留原始发布时间——「最新上架」的排序不该被下架再上架刷到最前。
        OffsetDateTime publishedAt =
                course.getPublishedAt() == null ? now : course.getPublishedAt();
        return changeStatus(
                course, CourseStatus.PUBLISHED, publishedAt, "COURSE_PUBLISHED", adminId, requestId, now);
    }

    @Transactional
    public AdminCourseDetail archive(long courseId, long adminId, String requestId) {
        CourseEntity course = requireCourse(courseId);
        return changeStatus(
                course,
                CourseStatus.ARCHIVED,
                course.getPublishedAt(),
                "COURSE_ARCHIVED",
                adminId,
                requestId,
                OffsetDateTime.now());
    }

    /**
     * 删除课程，并顺手清掉它独占的 COS 对象。
     *
     * <p>局限：markdown 正文里内联的插图**不会**被清理——那些对象键只出现在正文文本里，
     * 没有单独登记。这是已知的孤儿对象来源，需要时另做一个对账任务。</p>
     */
    @Transactional
    public void delete(long courseId, long adminId, String requestId) {
        CourseEntity course = requireCourse(courseId);
        OffsetDateTime now = OffsetDateTime.now();
        if (courseMapper.deleteById(courseId) != 1) {
            throw CourseErrors.courseNotFound();
        }
        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.ADMIN,
                adminId,
                "COURSE_DELETED",
                "COURSE",
                courseId,
                requestId,
                metadata(Map.of("title", course.getTitle())),
                now));

        deleteQuietly(course.getCoverObjectKey());
        deleteQuietly(course.getVideoObjectKey());
    }

    private AdminCourseDetail changeStatus(
            CourseEntity course,
            CourseStatus status,
            OffsetDateTime publishedAt,
            String action,
            long adminId,
            String requestId,
            OffsetDateTime now) {
        CourseEntity update = new CourseEntity();
        update.setId(course.getId());
        update.setStatus(status);
        update.setPublishedAt(publishedAt);
        update.setUpdatedByAdminId(adminId);
        update.setUpdatedAt(now);
        update.setVersion(course.getVersion());
        if (courseMapper.updateById(update) != 1) {
            throw CourseErrors.versionConflict();
        }
        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.ADMIN,
                adminId,
                action,
                "COURSE",
                course.getId(),
                requestId,
                metadata(Map.of("from", course.getStatus().name(), "to", status.name())),
                now));
        return detail(course.getId());
    }

    private CourseEntity requireCourse(long courseId) {
        CourseEntity course = courseMapper.selectById(courseId);
        if (course == null) {
            throw CourseErrors.courseNotFound();
        }
        return course;
    }

    /**
     * 服务层的内容校验。库里的 {@code ck_course_content} 是同一条规则的兜底——
     * 这里存在的意义是给出人话错误，而不是让运营吃一个约束名。
     */
    private static void validateContent(SaveCourseCommand command) {
        boolean hasBody = StringUtils.hasText(command.contentMarkdown())
                && !command.contentMarkdown().isBlank();
        switch (command.contentType()) {
            case ARTICLE, TEXT -> {
                if (!hasBody) {
                    throw CourseErrors.contentRequired();
                }
            }
            case VIDEO -> {
                if (!StringUtils.hasText(command.videoObjectKey())) {
                    throw CourseErrors.videoRequired();
                }
            }
        }
    }

    private static void applyCommand(CourseEntity course, SaveCourseCommand command) {
        course.setCollectionId(command.collectionId());
        course.setTitle(command.title().trim());
        course.setSubtitle(trimToNull(command.subtitle()));
        course.setSummary(trimToNull(command.summary()));
        course.setAuthorName(trimToNull(command.authorName()));
        course.setContentType(command.contentType());
        course.setContentMarkdown(trimToNull(command.contentMarkdown()));
        course.setCoverObjectKey(trimToNull(command.coverObjectKey()));
        course.setVideoObjectKey(trimToNull(command.videoObjectKey()));
        // 时长与大小只登记，不校验范围。
        course.setVideoDurationSeconds(command.videoDurationSeconds());
        course.setVideoSizeBytes(command.videoSizeBytes());
        course.setVideoContentType(trimToNull(command.videoContentType()));
        course.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
    }

    private AdminCourseListItem toListItem(CourseEntity course, Map<Long, String> collectionNames) {
        return new AdminCourseListItem(
                course.getId(),
                course.getCollectionId(),
                collectionNames.get(course.getCollectionId()),
                course.getTitle(),
                course.getSubtitle(),
                course.getContentType().name(),
                course.getStatus().name(),
                course.getAuthorName(),
                course.getVideoDurationSeconds(),
                course.getSortOrder() == null ? 0 : course.getSortOrder(),
                course.getPublishedAt(),
                course.getUpdatedAt(),
                course.getVersion() == null ? 0L : course.getVersion());
    }

    private AdminCourseDetail toDetail(CourseEntity course, Map<Long, String> collectionNames) {
        return new AdminCourseDetail(
                course.getId(),
                course.getCollectionId(),
                collectionNames.get(course.getCollectionId()),
                course.getTitle(),
                course.getSubtitle(),
                course.getSummary(),
                course.getAuthorName(),
                course.getContentType().name(),
                course.getStatus().name(),
                course.getContentMarkdown(),
                course.getCoverObjectKey(),
                sign(course.getCoverObjectKey()),
                course.getVideoObjectKey(),
                sign(course.getVideoObjectKey()),
                course.getVideoDurationSeconds(),
                course.getVideoSizeBytes(),
                course.getVideoContentType(),
                course.getSortOrder() == null ? 0 : course.getSortOrder(),
                course.getPublishedAt(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                course.getVersion() == null ? 0L : course.getVersion());
    }

    private String sign(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        return storageService.signDownloadUrl(objectKey, PREVIEW_TTL);
    }

    /** 清理路径：对象已经不在了也算达成目的，不该把删课这件事整个回滚掉。 */
    private void deleteQuietly(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            storageService.delete(objectKey);
        } catch (RuntimeException ignored) {
            // 对象存储没配或对象已删；课程行已经删掉了，这里不再抛。
        }
    }

    private static Map<String, Object> changes(CourseEntity before, SaveCourseCommand command) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (!before.getTitle().equals(command.title().trim())) {
            changes.put("title", command.title().trim());
        }
        if (before.getContentType() != command.contentType()) {
            changes.put("contentType", command.contentType().name());
        }
        if (!before.getCollectionId().equals(command.collectionId())) {
            changes.put("collectionId", command.collectionId());
        }
        return changes;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String metadata(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException error) {
            return "{}";
        }
    }
}
