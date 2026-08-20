package com.love.archive.admin.application;

import com.love.archive.admin.persistence.AdminProfileQueryMapper;
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
                rows.stream()
                        .map(row -> new AdminProfileListItem(
                                row.getId(),
                                row.getProfileNo(),
                                row.getPhone(),
                                row.getMembershipTier(),
                                row.getStatus(),
                                row.getUpdatedAt()))
                        .toList(),
                pageNumber,
                pageSize,
                total);
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
                row.getPhone(),
                row.getMembershipTier(),
                row.getMembershipCreditMinor() == null ? 0L : row.getMembershipCreditMinor(),
                row.getStatus(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getGender(),
                row.getBirthDate(),
                row.getHeightCm(),
                row.getEducation(),
                row.getOccupation(),
                row.getIncomeRange(),
                row.getCity(),
                row.getWechatId(),
                row.getDouyinId(),
                row.getDouyinNickname(),
                row.getDouyinProfileUrl(),
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
