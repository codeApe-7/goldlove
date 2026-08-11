package com.love.archive.guest.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.common.web.ApiException;
import com.love.archive.guest.domain.FieldStorageKind;
import com.love.archive.guest.domain.PhotoCategory;
import com.love.archive.guest.domain.ProfileStatus;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.guest.persistence.ProfileFieldDefinitionEntity;
import com.love.archive.guest.persistence.ProfileFieldDefinitionMapper;
import com.love.archive.guest.persistence.ProfileFieldValueEntity;
import com.love.archive.guest.persistence.ProfileFieldValueMapper;
import com.love.archive.guest.persistence.ProfilePhotoEntity;
import com.love.archive.guest.persistence.ProfilePhotoMapper;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class DatabaseGuestProfileSnapshotProvider implements GuestProfileSnapshotProvider {

    private final GuestProfileMapper profileMapper;
    private final ProfileFieldDefinitionMapper definitionMapper;
    private final ProfileFieldValueMapper valueMapper;
    private final ProfilePhotoMapper photoMapper;
    private final ProfileSubmissionReadinessValidator readinessValidator;

    @Override
    public GuestProfileSnapshot lockAndValidate(long accountId) {
        GuestProfileEntity profile = profileMapper.selectOwnedForUpdate(accountId);
        if (profile == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PROFILE_NOT_STARTED", "请先保存档案草稿");
        }
        if (profile.getStatus() != ProfileStatus.DRAFT
                && profile.getStatus() != ProfileStatus.CHANGES_REQUESTED
                && profile.getStatus() != ProfileStatus.APPROVED
                && profile.getStatus() != ProfileStatus.PENDING_REVIEW) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PROFILE_NOT_EDITABLE", "当前档案状态不允许提交");
        }

        List<ProfileFieldDefinitionEntity> enabledDefinitions = definitionMapper.selectList(
                Wrappers.<ProfileFieldDefinitionEntity>lambdaQuery()
                        .eq(ProfileFieldDefinitionEntity::getEnabled, true)
                        .orderByAsc(ProfileFieldDefinitionEntity::getSortOrder)
                        .orderByAsc(ProfileFieldDefinitionEntity::getId));
        List<ProfileFieldValueEntity> values = valueMapper.selectList(
                Wrappers.<ProfileFieldValueEntity>lambdaQuery()
                        .eq(ProfileFieldValueEntity::getGuestProfileId, profile.getId())
                        .orderByAsc(ProfileFieldValueEntity::getId));
        List<String> missing = readinessValidator.missingRequiredFieldCodes(
                profile, enabledDefinitions, values);
        if (!missing.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROFILE_VALIDATION_FAILED",
                    "缺少必填字段: " + String.join(",", missing));
        }

        List<ProfilePhotoEntity> photos = photoMapper.selectList(
                Wrappers.<ProfilePhotoEntity>lambdaQuery()
                        .eq(ProfilePhotoEntity::getGuestProfileId, profile.getId())
                        .orderByAsc(ProfilePhotoEntity::getCategory)
                        .orderByAsc(ProfilePhotoEntity::getSortOrder));
        boolean hasAvatar = photos.stream().anyMatch(
                photo -> photo.getCategory() == PhotoCategory.AVATAR);
        if (!hasAvatar) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROFILE_VALIDATION_FAILED",
                    "缺少必填字段: avatar");
        }

        Map<Long, ProfileFieldValueEntity> valuesByDefinition = new HashMap<>();
        for (ProfileFieldValueEntity value : values) {
            valuesByDefinition.put(value.getFieldDefinitionId(), value);
        }
        List<GuestProfileSnapshot.FieldValue> dynamicFields = enabledDefinitions.stream()
                .filter(definition -> definition.getStorageKind() == FieldStorageKind.DYNAMIC)
                .map(definition -> toSnapshotValue(
                        definition, valuesByDefinition.get(definition.getId())))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(GuestProfileSnapshot.FieldValue::fieldCode))
                .toList();
        List<GuestProfileSnapshot.Photo> photoSnapshots = photos.stream()
                .map(photo -> new GuestProfileSnapshot.Photo(
                        photo.getCategory().name(),
                        photo.getObjectKey(),
                        photo.getSortOrder()))
                .toList();

        return new GuestProfileSnapshot(
                profile.getId(),
                profile.getUserAccountId(),
                profile.getVersion(),
                profile.getPendingRevisionId(),
                profile.getGender(),
                profile.getBirthDate(),
                profile.getHeightCm(),
                profile.getEducation(),
                profile.getOccupation(),
                profile.getIncomeRange(),
                profile.getCity(),
                profile.getWechatIdCiphertext(),
                profile.getWechatIdHmac(),
                profile.getDouyinIdCiphertext(),
                profile.getDouyinIdHmac(),
                profile.getDouyinNicknameCiphertext(),
                profile.getDouyinProfileUrlCiphertext(),
                dynamicFields,
                photoSnapshots);
    }

    private static GuestProfileSnapshot.FieldValue toSnapshotValue(
            ProfileFieldDefinitionEntity definition,
            ProfileFieldValueEntity value) {
        if (value == null) {
            return null;
        }
        return new GuestProfileSnapshot.FieldValue(
                definition.getFieldCode(),
                definition.getLabel(),
                definition.getDataType().name(),
                value.getOptionValue(),
                value.getTextValue(),
                value.getIntegerValue(),
                value.getDecimalValue(),
                value.getDateValue(),
                value.getBooleanValue(),
                value.getOptionValue());
    }
}
