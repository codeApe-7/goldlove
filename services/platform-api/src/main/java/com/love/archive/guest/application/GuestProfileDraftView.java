package com.love.archive.guest.application;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GuestProfileDraftView(
        UUID profileNo,
        String status,
        Long version,
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
        URI douyinProfileUrl,
        List<String> missingRequiredFieldCodes,
        List<ProfileFieldValueView> dynamicFields) {

    public GuestProfileDraftView {
        missingRequiredFieldCodes = List.copyOf(missingRequiredFieldCodes);
        dynamicFields = List.copyOf(dynamicFields);
    }
}
