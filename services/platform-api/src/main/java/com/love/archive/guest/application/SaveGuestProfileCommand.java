package com.love.archive.guest.application;

import java.util.List;

public record SaveGuestProfileCommand(
        Long expectedVersion,
        String gender,
        Integer age,
        Integer heightCm,
        String education,
        String occupation,
        String incomeRange,
        String city,
        String wechatId,
        String douyinId,
        List<ProfileFieldInput> dynamicFields,
        ProfilePhotoTarget photos) {

    public SaveGuestProfileCommand {
        dynamicFields = dynamicFields == null ? List.of() : List.copyOf(dynamicFields);
        photos = photos == null ? ProfilePhotoTarget.empty() : photos;
    }

    public SaveGuestProfileCommand(
            Long expectedVersion,
            String gender,
            Integer age,
            Integer heightCm,
            String education,
            String occupation,
            String incomeRange,
            String city,
            String wechatId,
            String douyinId,
            List<ProfileFieldInput> dynamicFields) {
        this(expectedVersion, gender, age, heightCm, education, occupation,
                incomeRange, city, wechatId, douyinId, dynamicFields,
                ProfilePhotoTarget.empty());
    }
}
