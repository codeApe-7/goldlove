package com.love.archive.admin.application;

import com.love.archive.admin.persistence.AdminProfileQueryMapper;
import com.love.archive.admin.persistence.query.AdminProfileCountsRow;
import com.love.archive.admin.persistence.query.AdminProfileDetailRow;
import com.love.archive.admin.persistence.query.AdminProfileFieldRow;
import com.love.archive.admin.persistence.query.AdminProfileListRow;
import com.love.archive.admin.persistence.query.AdminProfilePhotoRow;
import com.love.archive.common.web.ApiException;
import com.love.archive.common.web.PageView;
import com.love.archive.storage.application.ObjectStorageService;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员查看所有档案。档案没有审核环节，保存即对管理员可见，
 * 未填完的（DRAFT）同样出现在列表里。
 */
@Service
@RequiredArgsConstructor
public class AdminProfileQueryService {

    private static final Duration PREVIEW_TTL = Duration.ofMinutes(15);
    private static final long MAX_PAGE_SIZE = 100;

    private final AdminProfileQueryMapper profileQueryMapper;
    private final ObjectStorageService storageService;

    @Transactional(readOnly = true)
    public PageView<AdminProfileListItem> list(
            AdminProfileFilter filter, long requestedPage, long requestedSize) {
        long pageNumber = Math.max(1, requestedPage);
        long pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, requestedSize));
        List<AdminProfileListRow> rows = profileQueryMapper.search(
                filter, pageSize, (pageNumber - 1) * pageSize);
        long total = profileQueryMapper.count(filter);
        return new PageView<>(
                rows.stream().map(AdminProfileQueryService::toListItem).toList(),
                pageNumber,
                pageSize,
                total);
    }

    /** tab 上的数量。用筛选条上的条件统计，不含 tab 自身条件。 */
    @Transactional(readOnly = true)
    public AdminProfileCounts counts(AdminProfileFilter filter) {
        AdminProfileCountsRow row = profileQueryMapper.counts(filter.withoutTabConditions());
        if (row == null) {
            return new AdminProfileCounts(0, 0, 0, 0, 0);
        }
        return new AdminProfileCounts(
                orZero(row.getTotal()),
                orZero(row.getDraft()),
                orZero(row.getCompleted()),
                orZero(row.getSuspended()),
                orZero(row.getPaid()));
    }

    private static AdminProfileListItem toListItem(AdminProfileListRow row) {
        return new AdminProfileListItem(
                row.getId(),
                row.getProfileNo(),
                row.getUserAccountId(),
                row.getPhone(),
                row.getMembershipTier(),
                row.getAccountStatus(),
                row.getStatus(),
                row.getGender(),
                row.getAge(),
                row.getCity(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    @Transactional(readOnly = true)
    public AdminProfileDetail detail(long profileId) {
        AdminProfileDetailRow row = profileQueryMapper.detail(profileId);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "档案不存在");
        }
        return new AdminProfileDetail(
                row.getId(),
                row.getProfileNo(),
                row.getUserAccountId(),
                row.getPhone(),
                row.getMembershipTier(),
                row.getMembershipCreditMinor() == null ? 0L : row.getMembershipCreditMinor(),
                row.getAccountStatus(),
                row.getStatus(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getGender(),
                row.getAge(),
                row.getHeightCm(),
                row.getEducation(),
                row.getOccupation(),
                row.getIncomeRange(),
                row.getCity(),
                row.getWechatId(),
                row.getDouyinId(),
                dynamicFields(profileId),
                photos(profileId));
    }

    private List<AdminProfileFieldValue> dynamicFields(long profileId) {
        return profileQueryMapper.dynamicFields(profileId).stream()
                .map(row -> new AdminProfileFieldValue(
                        row.getFieldCode(), row.getLabel(), row.getDataType(), presentValue(row)))
                .toList();
    }

    private List<AdminProfilePhoto> photos(long profileId) {
        return profileQueryMapper.photos(profileId).stream()
                .map(row -> new AdminProfilePhoto(
                        row.getId(),
                        row.getCategory(),
                        row.getSortOrder() == null ? 0 : row.getSortOrder(),
                        // 对象键只留在服务端，下发的只有 15 分钟内有效的签名地址。
                        storageService.signDownloadUrl(row.getObjectKey(), PREVIEW_TTL)))
                .toList();
    }

    private static String presentValue(AdminProfileFieldRow row) {
        if (row.getTextValue() != null) {
            return row.getTextValue();
        }
        if (row.getIntegerValue() != null) {
            return String.valueOf(row.getIntegerValue());
        }
        if (row.getDecimalValue() != null) {
            return row.getDecimalValue().toPlainString();
        }
        if (row.getDateValue() != null) {
            return row.getDateValue().toString();
        }
        if (row.getBooleanValue() != null) {
            return String.valueOf(row.getBooleanValue());
        }
        return row.getOptionValue();
    }
}
