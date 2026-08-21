package com.love.archive.guest.application;

import com.love.archive.guest.domain.FieldStorageKind;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.ProfileFieldDefinitionEntity;
import com.love.archive.guest.persistence.ProfileFieldValueEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class ProfileSubmissionReadinessValidator {

    public List<String> missingRequiredFieldCodes(
            GuestProfileEntity profile,
            List<ProfileFieldDefinitionEntity> definitions,
            List<ProfileFieldValueEntity> values) {
        Set<Long> populatedDefinitionIds = new HashSet<>();
        for (ProfileFieldValueEntity value : values) {
            populatedDefinitionIds.add(value.getFieldDefinitionId());
        }

        List<String> missing = new ArrayList<>();
        definitions.stream()
                .filter(definition -> Boolean.TRUE.equals(definition.getEnabled()))
                .filter(definition -> Boolean.TRUE.equals(definition.getRequired()))
                .sorted(Comparator.comparing(ProfileFieldDefinitionEntity::getSortOrder)
                        .thenComparing(ProfileFieldDefinitionEntity::getId))
                .forEach(definition -> {
                    boolean present = definition.getStorageKind() == FieldStorageKind.CORE
                            ? coreValuePresent(profile, definition.getFieldCode())
                            : populatedDefinitionIds.contains(definition.getId());
                    if (!present) {
                        missing.add(definition.getFieldCode());
                    }
                });
        return List.copyOf(missing);
    }

    private static boolean coreValuePresent(GuestProfileEntity profile, String fieldCode) {
        return switch (fieldCode) {
            case "gender" -> hasText(profile.getGender());
            case "birth_date" -> profile.getBirthDate() != null;
            case "height_cm" -> profile.getHeightCm() != null;
            case "education" -> hasText(profile.getEducation());
            case "occupation" -> hasText(profile.getOccupation());
            case "income_range" -> hasText(profile.getIncomeRange());
            case "city" -> hasText(profile.getCity());
            case "wechat_id" -> hasText(profile.getWechatId());
            case "douyin_id" -> hasText(profile.getDouyinId());
            case "douyin_nickname" -> hasText(profile.getDouyinNickname());
            case "douyin_profile_url" -> hasText(profile.getDouyinProfileUrl());
            default -> false;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
