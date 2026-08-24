package com.love.archive.guest.application;

import java.util.List;
import java.util.UUID;

public record GuestProfileDraftView(
        UUID profileNo,
        String status,
        Long version,
        String gender,
        Integer age,
        Integer heightCm,
        String education,
        String occupation,
        String incomeRange,
        String city,
        String wechatId,
        String douyinId,
        List<String> missingRequiredFieldCodes,
        List<ProfileFieldValueView> dynamicFields) {

    public GuestProfileDraftView {
        missingRequiredFieldCodes = List.copyOf(missingRequiredFieldCodes);
        dynamicFields = List.copyOf(dynamicFields);
    }
}
