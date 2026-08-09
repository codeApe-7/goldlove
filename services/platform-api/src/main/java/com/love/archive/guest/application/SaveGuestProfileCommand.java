package com.love.archive.guest.application;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

public record SaveGuestProfileCommand(
        Long expectedVersion,
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
        List<ProfileFieldInput> dynamicFields) {

    public SaveGuestProfileCommand {
        dynamicFields = dynamicFields == null ? List.of() : List.copyOf(dynamicFields);
    }
}
