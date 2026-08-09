package com.love.archive.review.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.security.SensitiveValueProtector;
import com.love.archive.common.web.ApiException;
import com.love.archive.common.web.PageView;
import com.love.archive.guest.application.GuestProfileApprovalPort;
import com.love.archive.review.domain.ReviewResult;
import com.love.archive.review.domain.RevisionStatus;
import com.love.archive.review.persistence.ProfileReviewQueryMapper;
import com.love.archive.review.persistence.ProfileReviewRecordEntity;
import com.love.archive.review.persistence.ProfileReviewRecordMapper;
import com.love.archive.review.persistence.ProfileRevisionEntity;
import com.love.archive.review.persistence.ProfileRevisionMapper;
import com.love.archive.review.persistence.ProfileRevisionPhotoEntity;
import com.love.archive.review.persistence.ProfileRevisionPhotoMapper;
import com.love.archive.review.persistence.query.ProfileRevisionFieldRow;
import com.love.archive.review.persistence.query.ProfileReviewHeaderRow;
import com.love.archive.review.persistence.query.ProfileReviewListRow;
import com.love.archive.storage.application.ObjectStorageService;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileReviewService {

    private static final String WECHAT_ID_DOMAIN = "profile:wechat-id";
    private static final String DOUYIN_ID_DOMAIN = "profile:douyin-id";
    private static final String DOUYIN_NICKNAME_DOMAIN = "profile:douyin-nickname";
    private static final String DOUYIN_PROFILE_URL_DOMAIN = "profile:douyin-profile-url";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int REJECT_COMMENT_MAX = 1000;
    private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private final ProfileReviewQueryMapper queryMapper;
    private final ProfileRevisionMapper revisionMapper;
    private final ProfileRevisionPhotoMapper revisionPhotoMapper;
    private final ProfileReviewRecordMapper reviewRecordMapper;
    private final GuestProfileApprovalPort profileApprovalPort;
    private final SensitiveValueProtector protector;
    private final AuditTrail auditTrail;
    private final ObjectStorageService storageService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageView<ProfileReviewListItem> search(
            ProfileReviewFilter filter,
            long requestedPage,
            long requestedSize) {
        long pageNumber = Math.max(1, requestedPage);
        long pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, requestedSize));
        Page<ProfileReviewListRow> page = Page.of(pageNumber, pageSize);
        OffsetDateTime now = OffsetDateTime.now(clock);
        queryMapper.search(page, filter, now);
        long total = queryMapper.count(filter, now);
        return new PageView<>(
                page.getRecords().stream().map(this::toListItem).toList(),
                pageNumber,
                pageSize,
                total);
    }

    @Transactional(readOnly = true)
    public PageView<ProfileReviewListItem> search(ProfileReviewFilter filter) {
        return search(filter, 1, 20);
    }

    @Transactional(readOnly = true)
    public ProfileReviewDetail detail(long revisionId) {
        ProfileReviewHeaderRow header = queryMapper.detailHeader(revisionId);
        if (header == null) {
            throw revisionNotFound();
        }
        ProfileRevisionEntity pending = revisionMapper.selectById(revisionId);
        ProfileRevisionEntity approved = header.currentApprovedRevisionId() == null
                ? null
                : revisionMapper.selectById(header.currentApprovedRevisionId());
        List<ProfileRevisionFieldRow> pendingFields = queryMapper.revisionFields(revisionId);
        List<ProfileRevisionFieldRow> approvedFields = approved == null
                ? List.of()
                : queryMapper.revisionFields(approved.getId());
        List<ProfileFieldDifference> differences = approved == null
                ? List.of()
                : buildDifferences(pending, approved, pendingFields, approvedFields);
        List<ProfileRevisionView.Photo> photos = revisionPhotoMapper.selectList(
                        Wrappers.<ProfileRevisionPhotoEntity>lambdaQuery()
                                .eq(ProfileRevisionPhotoEntity::getProfileRevisionId,
                                        revisionId)
                                .orderByAsc(ProfileRevisionPhotoEntity::getCategory)
                                .orderByAsc(ProfileRevisionPhotoEntity::getSortOrder))
                .stream()
                .map(photo -> new ProfileRevisionView.Photo(
                        photo.getCategory().name(),
                        photo.getSha256(),
                        photo.getSizeBytes(),
                        photo.getContentType(),
                        photo.getWidth(),
                        photo.getHeight(),
                        photo.getSortOrder(),
                        storageService.signDownloadUrl(
                                photo.getObjectKey(), Duration.ofMinutes(15))))
                .toList();
        return toDetail(header, pending, pendingFields, photos, differences);
    }

    @Transactional
    public ProfileReviewDecisionView approve(
            long adminId,
            long revisionId,
            long expectedVersion,
            String requestId) {
        ProfileRevisionEntity revision = lockRevision(revisionId);
        ProfileReviewDecisionView replay = resolveCompletedDecision(revision, ReviewResult.APPROVED);
        if (replay != null) {
            return replay;
        }
        OffsetDateTime reviewedAt = OffsetDateTime.now(clock);
        updatePendingRevision(revisionId, expectedVersion, RevisionStatus.APPROVED, reviewedAt);
        appendReviewRecord(adminId, revisionId, ReviewResult.APPROVED, null, null, requestId, reviewedAt);
        profileApprovalPort.approve(
                revision.getGuestProfileId(), revisionId, profileVersion(revision.getGuestProfileId()));
        appendAudit(adminId, revisionId, "PROFILE_APPROVED",
                "{\"result\":\"APPROVED\"}", requestId, reviewedAt);
        return decisionView(revisionId);
    }

    @Transactional
    public ProfileReviewDecisionView reject(
            long adminId,
            long revisionId,
            long expectedVersion,
            String reasonCode,
            String comment,
            String requestId) {
        String normalizedComment = requireRejectComment(comment);
        String normalizedReason = normalizeReasonCode(reasonCode);
        ProfileRevisionEntity revision = lockRevision(revisionId);
        ProfileReviewDecisionView replay = resolveCompletedDecision(revision, ReviewResult.REJECTED);
        if (replay != null) {
            return replay;
        }
        OffsetDateTime reviewedAt = OffsetDateTime.now(clock);
        updatePendingRevision(revisionId, expectedVersion, RevisionStatus.REJECTED, reviewedAt);
        appendReviewRecord(adminId, revisionId, ReviewResult.REJECTED,
                normalizedReason, normalizedComment, requestId, reviewedAt);
        profileApprovalPort.reject(
                revision.getGuestProfileId(), revisionId, profileVersion(revision.getGuestProfileId()));
        appendAudit(adminId, revisionId, "PROFILE_REJECTED",
                "{\"result\":\"REJECTED\",\"reasonCode\":"
                        + (normalizedReason == null ? "null" : "\"" + normalizedReason + "\"") + "}",
                requestId, reviewedAt);
        return decisionView(revisionId);
    }

    private ProfileRevisionEntity lockRevision(long revisionId) {
        ProfileRevisionEntity revision = revisionMapper.selectByIdForUpdate(revisionId);
        if (revision == null) {
            throw revisionNotFound();
        }
        return revision;
    }

    private ProfileReviewDecisionView resolveCompletedDecision(
            ProfileRevisionEntity revision,
            ReviewResult requested) {
        if (revision.getStatus() == RevisionStatus.PENDING) {
            return null;
        }
        ProfileReviewRecordEntity record = reviewRecordMapper.selectOne(
                Wrappers.<ProfileReviewRecordEntity>lambdaQuery()
                        .eq(ProfileReviewRecordEntity::getProfileRevisionId, revision.getId()));
        ReviewResult actual = record == null ? null : record.getResult();
        if (actual != requested) {
            throw alreadyCompleted();
        }
        return decisionView(revision.getId());
    }

    private void updatePendingRevision(
            long revisionId,
            long expectedVersion,
            RevisionStatus status,
            OffsetDateTime reviewedAt) {
        int updated = revisionMapper.update(Wrappers.<ProfileRevisionEntity>lambdaUpdate()
                .eq(ProfileRevisionEntity::getId, revisionId)
                .eq(ProfileRevisionEntity::getStatus, RevisionStatus.PENDING)
                .eq(ProfileRevisionEntity::getVersion, expectedVersion)
                .set(ProfileRevisionEntity::getStatus, status)
                .set(ProfileRevisionEntity::getReviewedAt, reviewedAt)
                .set(ProfileRevisionEntity::getVersion, expectedVersion + 1));
        if (updated != 1) {
            throw revisionVersionConflict();
        }
    }

    private void appendReviewRecord(
            long adminId,
            long revisionId,
            ReviewResult result,
            String reasonCode,
            String comment,
            String requestId,
            OffsetDateTime reviewedAt) {
        ProfileReviewRecordEntity record = new ProfileReviewRecordEntity();
        record.setProfileRevisionId(revisionId);
        record.setReviewerAdminId(adminId);
        record.setResult(result);
        record.setReasonCode(reasonCode);
        record.setComment(comment);
        record.setReviewedAt(reviewedAt);
        record.setRequestId(requestId);
        record.setCreatedAt(reviewedAt);
        try {
            reviewRecordMapper.insert(record);
        } catch (DataIntegrityViolationException exception) {
            throw alreadyCompleted();
        }
    }

    private long profileVersion(long profileId) {
        Long version = queryMapper.profileVersion(profileId);
        if (version == null) {
            throw revisionNotFound();
        }
        return version;
    }

    private void appendAudit(
            long adminId,
            long revisionId,
            String action,
            String metadata,
            String requestId,
            OffsetDateTime occurredAt) {
        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.ADMIN,
                adminId,
                action,
                "PROFILE_REVISION",
                revisionId,
                requestId,
                metadata,
                occurredAt));
    }

    private ProfileReviewDecisionView decisionView(long revisionId) {
        ProfileRevisionEntity saved = revisionMapper.selectById(revisionId);
        return new ProfileReviewDecisionView(
                saved.getId(),
                saved.getRevisionNumber(),
                saved.getStatus(),
                saved.getReviewedAt(),
                saved.getVersion());
    }

    private ProfileReviewListItem toListItem(ProfileReviewListRow row) {
        return new ProfileReviewListItem(
                row.revisionId(),
                row.revisionNumber(),
                row.status(),
                row.submittedAt(),
                row.reviewDeadlineAt(),
                row.profileNo(),
                row.currentApprovedRevisionId());
    }

    private ProfileReviewDetail toDetail(
            ProfileReviewHeaderRow header,
            ProfileRevisionEntity pending,
            List<ProfileRevisionFieldRow> pendingFields,
            List<ProfileRevisionView.Photo> photos,
            List<ProfileFieldDifference> differences) {
        return new ProfileReviewDetail(
                header.profileNo(),
                pending.getId(),
                pending.getRevisionNumber(),
                pending.getStatus(),
                pending.getGender(),
                pending.getBirthDate(),
                pending.getHeightCm(),
                pending.getEducation(),
                pending.getOccupation(),
                pending.getIncomeRange(),
                pending.getCity(),
                decrypt(WECHAT_ID_DOMAIN, pending.getWechatIdCiphertext()),
                decrypt(DOUYIN_ID_DOMAIN, pending.getDouyinIdCiphertext()),
                decrypt(DOUYIN_NICKNAME_DOMAIN, pending.getDouyinNicknameCiphertext()),
                toUri(decrypt(DOUYIN_PROFILE_URL_DOMAIN, pending.getDouyinProfileUrlCiphertext())),
                pending.getSubmittedAt(),
                pending.getReviewDeadlineAt(),
                pending.getReviewedAt(),
                pending.getVersion(),
                toFieldValues(pendingFields),
                header.currentApprovedRevisionId(),
                photos,
                differences);
    }

    private List<ProfileFieldDifference> buildDifferences(
            ProfileRevisionEntity pending,
            ProfileRevisionEntity approved,
            List<ProfileRevisionFieldRow> pendingFields,
            List<ProfileRevisionFieldRow> approvedFields) {
        List<ProfileFieldDifference> differences = new ArrayList<>();
        addDifference(differences, "gender", "性别",
                approved.getGender(), pending.getGender());
        addDifference(differences, "birth_date", "出生日期",
                approved.getBirthDate(), pending.getBirthDate());
        addDifference(differences, "height_cm", "身高",
                approved.getHeightCm(), pending.getHeightCm());
        addDifference(differences, "education", "学历",
                approved.getEducation(), pending.getEducation());
        addDifference(differences, "occupation", "职业",
                approved.getOccupation(), pending.getOccupation());
        addDifference(differences, "income_range", "收入范围",
                approved.getIncomeRange(), pending.getIncomeRange());
        addDifference(differences, "city", "所在城市",
                approved.getCity(), pending.getCity());
        addDifference(differences, "wechat_id", "微信号",
                decrypt(WECHAT_ID_DOMAIN, approved.getWechatIdCiphertext()),
                decrypt(WECHAT_ID_DOMAIN, pending.getWechatIdCiphertext()));
        addDifference(differences, "douyin_id", "抖音号",
                decrypt(DOUYIN_ID_DOMAIN, approved.getDouyinIdCiphertext()),
                decrypt(DOUYIN_ID_DOMAIN, pending.getDouyinIdCiphertext()));
        addDifference(differences, "douyin_nickname", "抖音昵称",
                decrypt(DOUYIN_NICKNAME_DOMAIN, approved.getDouyinNicknameCiphertext()),
                decrypt(DOUYIN_NICKNAME_DOMAIN, pending.getDouyinNicknameCiphertext()));
        addDifference(differences, "douyin_profile_url", "抖音主页链接",
                decrypt(DOUYIN_PROFILE_URL_DOMAIN, approved.getDouyinProfileUrlCiphertext()),
                decrypt(DOUYIN_PROFILE_URL_DOMAIN, pending.getDouyinProfileUrlCiphertext()));

        Map<String, ProfileRevisionFieldRow> approvedByCode = indexByCode(approvedFields);
        Map<String, ProfileRevisionFieldRow> pendingByCode = indexByCode(pendingFields);
        TreeSet<String> codes = new TreeSet<>(pendingByCode.keySet());
        codes.addAll(approvedByCode.keySet());
        for (String fieldCode : codes) {
            ProfileRevisionFieldRow oldRow = approvedByCode.get(fieldCode);
            ProfileRevisionFieldRow newRow = pendingByCode.get(fieldCode);
            String oldValue = oldRow == null ? null : displayValue(oldRow);
            String newValue = newRow == null ? null : displayValue(newRow);
            if (!Objects.equals(oldValue, newValue)) {
                String label = newRow == null ? oldRow.fieldLabel() : newRow.fieldLabel();
                differences.add(new ProfileFieldDifference(fieldCode, label, oldValue, newValue));
            }
        }
        return List.copyOf(differences);
    }

    private static Map<String, ProfileRevisionFieldRow> indexByCode(
            List<ProfileRevisionFieldRow> rows) {
        return rows.stream().collect(Collectors.toMap(
                ProfileRevisionFieldRow::fieldCode, Function.identity()));
    }

    private static String displayValue(ProfileRevisionFieldRow row) {
        if (row.textValue() != null) {
            return row.textValue();
        }
        if (row.integerValue() != null) {
            return String.valueOf(row.integerValue());
        }
        if (row.decimalValue() != null) {
            return row.decimalValue().toPlainString();
        }
        if (row.dateValue() != null) {
            return row.dateValue().toString();
        }
        if (row.booleanValue() != null) {
            return String.valueOf(row.booleanValue());
        }
        if (row.optionValue() != null) {
            return row.optionValue();
        }
        return null;
    }

    private static List<ProfileRevisionView.FieldValue> toFieldValues(
            List<ProfileRevisionFieldRow> rows) {
        return rows.stream()
                .map(row -> new ProfileRevisionView.FieldValue(
                        row.fieldCode(),
                        row.fieldLabel(),
                        row.dataType(),
                        row.displayOption(),
                        row.textValue(),
                        row.integerValue(),
                        row.decimalValue(),
                        row.dateValue(),
                        row.booleanValue(),
                        row.optionValue()))
                .toList();
    }

    private static void addDifference(
            List<ProfileFieldDifference> differences,
            String fieldCode,
            String fieldLabel,
            Object oldValue,
            Object newValue) {
        String oldText = oldValue == null ? null : String.valueOf(oldValue);
        String newText = newValue == null ? null : String.valueOf(newValue);
        if (!Objects.equals(oldText, newText)) {
            differences.add(new ProfileFieldDifference(fieldCode, fieldLabel, oldText, newText));
        }
    }

    private String decrypt(String domain, byte[] ciphertext) {
        return ciphertext == null ? null : protector.decrypt(domain, ciphertext);
    }

    private static URI toUri(String value) {
        return value == null ? null : URI.create(value);
    }

    private static String requireRejectComment(String comment) {
        if (comment == null || comment.isBlank() || comment.trim().length() > REJECT_COMMENT_MAX) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FIELD_VALUE_INVALID",
                    "退回说明不能为空且不能超过 1000 个字符");
        }
        return comment.trim();
    }

    private static String normalizeReasonCode(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return null;
        }
        String normalized = reasonCode.trim();
        if (normalized.length() > 64 || !REASON_CODE.matcher(normalized).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FIELD_VALUE_INVALID",
                    "原因代码格式不正确");
        }
        return normalized;
    }

    private static ApiException revisionNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "PROFILE_REVISION_NOT_FOUND", "档案版本不存在");
    }

    private static ApiException revisionVersionConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "PROFILE_VERSION_CONFLICT",
                "审核版本已发生变化，请刷新后重试");
    }

    private static ApiException alreadyCompleted() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "PROFILE_REVIEW_ALREADY_COMPLETED",
                "该档案版本已完成审核");
    }
}
