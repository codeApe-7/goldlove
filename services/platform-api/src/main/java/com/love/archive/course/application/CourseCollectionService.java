package com.love.archive.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.course.domain.CourseCollectionStatus;
import com.love.archive.course.persistence.CourseCollectionEntity;
import com.love.archive.course.persistence.CourseCollectionMapper;
import com.love.archive.course.persistence.CourseMapper;
import com.love.archive.course.persistence.query.CourseCollectionCountRow;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 合集的读写。
 *
 * <p>合集不能删——下面挂着课程，删行会留孤儿，库里也没给 {@code archive_app} DELETE 权限。
 * 下线走 {@code status = HIDDEN}。</p>
 */
@Service
@RequiredArgsConstructor
public class CourseCollectionService {

    private final CourseCollectionMapper collectionMapper;
    private final CourseMapper courseMapper;
    private final AuditTrail auditTrail;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<AdminCourseCollectionView> listForAdmin() {
        Map<Long, CourseCollectionCountRow> counts = countsByCollectionId();
        return orderedCollections(null).stream()
                .map(collection -> toAdminView(collection, counts.get(collection.getId())))
                .toList();
    }

    /** 访客只看得到启用的合集，课程数只算已发布的。 */
    @Transactional(readOnly = true)
    public List<GuestCourseCollectionView> listForGuest() {
        Map<Long, CourseCollectionCountRow> counts = countsByCollectionId();
        return orderedCollections(CourseCollectionStatus.ACTIVE).stream()
                .map(collection -> new GuestCourseCollectionView(
                        collection.getId(),
                        collection.getName(),
                        collection.getDescription(),
                        publishedCount(counts.get(collection.getId()))))
                .toList();
    }

    @Transactional
    public AdminCourseCollectionView create(
            String name, String description, Integer sortOrder, long adminId, String requestId) {
        String normalizedName = requireName(name);
        if (existsByName(normalizedName, null)) {
            throw CourseErrors.collectionNameDuplicate();
        }

        OffsetDateTime now = OffsetDateTime.now();
        CourseCollectionEntity entity = new CourseCollectionEntity();
        entity.setName(normalizedName);
        entity.setDescription(trimToNull(description));
        entity.setSortOrder(sortOrder == null ? 0 : sortOrder);
        entity.setStatus(CourseCollectionStatus.ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            collectionMapper.insert(entity);
        } catch (DuplicateKeyException duplicate) {
            // 上面查过一次，但并发下仍可能撞唯一约束——以库为准。
            throw CourseErrors.collectionNameDuplicate();
        }

        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.ADMIN,
                adminId,
                "COURSE_COLLECTION_CREATED",
                "COURSE_COLLECTION",
                entity.getId(),
                requestId,
                metadata(Map.of("name", normalizedName)),
                now));
        return toAdminView(entity, null);
    }

    @Transactional
    public AdminCourseCollectionView update(
            long collectionId,
            String name,
            String description,
            Integer sortOrder,
            CourseCollectionStatus status,
            long adminId,
            String requestId) {
        CourseCollectionEntity entity = collectionMapper.selectById(collectionId);
        if (entity == null) {
            throw CourseErrors.collectionNotFound();
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        if (name != null) {
            String normalizedName = requireName(name);
            if (!normalizedName.equals(entity.getName())) {
                if (existsByName(normalizedName, collectionId)) {
                    throw CourseErrors.collectionNameDuplicate();
                }
                changes.put("name", normalizedName);
                entity.setName(normalizedName);
            }
        }
        if (description != null) {
            entity.setDescription(trimToNull(description));
            changes.put("description", entity.getDescription());
        }
        if (sortOrder != null) {
            entity.setSortOrder(sortOrder);
            changes.put("sortOrder", sortOrder);
        }
        if (status != null && status != entity.getStatus()) {
            entity.setStatus(status);
            changes.put("status", status.name());
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (!changes.isEmpty()) {
            entity.setUpdatedAt(now);
            try {
                collectionMapper.updateById(entity);
            } catch (DuplicateKeyException duplicate) {
                throw CourseErrors.collectionNameDuplicate();
            }
            auditTrail.append(new AuditEvent(
                    AuditEvent.ActorType.ADMIN,
                    adminId,
                    "COURSE_COLLECTION_UPDATED",
                    "COURSE_COLLECTION",
                    collectionId,
                    requestId,
                    metadata(changes),
                    now));
        }
        return toAdminView(entity, countsByCollectionId().get(collectionId));
    }

    /** 供课程服务校验「合集存在吗」，顺带把名字带回去装配视图。 */
    @Transactional(readOnly = true)
    public CourseCollectionEntity requireCollection(long collectionId) {
        CourseCollectionEntity entity = collectionMapper.selectById(collectionId);
        if (entity == null) {
            throw CourseErrors.collectionNotFound();
        }
        return entity;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> namesById() {
        return collectionMapper.selectList(Wrappers.emptyWrapper()).stream()
                .collect(Collectors.toMap(
                        CourseCollectionEntity::getId, CourseCollectionEntity::getName));
    }

    private List<CourseCollectionEntity> orderedCollections(CourseCollectionStatus status) {
        return collectionMapper.selectList(Wrappers.<CourseCollectionEntity>lambdaQuery()
                .eq(status != null, CourseCollectionEntity::getStatus, status)
                .orderByAsc(CourseCollectionEntity::getSortOrder)
                .orderByAsc(CourseCollectionEntity::getId));
    }

    private Map<Long, CourseCollectionCountRow> countsByCollectionId() {
        return courseMapper.countByCollection().stream()
                .collect(Collectors.toMap(
                        CourseCollectionCountRow::getCollectionId, Function.identity()));
    }

    private boolean existsByName(String name, Long excludedId) {
        return collectionMapper.exists(Wrappers.<CourseCollectionEntity>lambdaQuery()
                .eq(CourseCollectionEntity::getName, name)
                .ne(excludedId != null, CourseCollectionEntity::getId, excludedId));
    }

    private AdminCourseCollectionView toAdminView(
            CourseCollectionEntity entity, CourseCollectionCountRow counts) {
        return new AdminCourseCollectionView(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getSortOrder() == null ? 0 : entity.getSortOrder(),
                entity.getStatus().name(),
                counts == null || counts.getCourseCount() == null ? 0L : counts.getCourseCount(),
                publishedCount(counts),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private static long publishedCount(CourseCollectionCountRow counts) {
        return counts == null || counts.getPublishedCourseCount() == null
                ? 0L
                : counts.getPublishedCourseCount();
    }

    private static String requireName(String name) {
        String trimmed = trimToNull(name);
        if (trimmed == null) {
            throw new com.love.archive.common.web.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "COURSE_COLLECTION_NAME_REQUIRED",
                    "合集名称不能为空");
        }
        return trimmed;
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
