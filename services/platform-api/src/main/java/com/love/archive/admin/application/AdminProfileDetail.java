package com.love.archive.admin.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminProfileDetail(
        long id,
        UUID profileNo,
        String phone,
        String membershipTier,
        long membershipCreditMinor,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String gender,
        LocalDate birthDate,
        Integer heightCm,
        String education,
        String occupation,
        String incomeRange,
        String city,
        String wechatId,
        String douyinId,
        String douyinNickname,
        String douyinProfileUrl,
        List<AdminProfileFieldValue> dynamicFields,
        List<AdminProfilePhoto> photos) {

    public AdminProfileDetail {
        dynamicFields = List.copyOf(dynamicFields);
        photos = List.copyOf(photos);
    }
}
