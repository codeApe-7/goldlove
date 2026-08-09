package com.love.archive.guest.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record GuestProfileSnapshot(
        long profileId,
        long accountId,
        long profileVersion,
        Long pendingRevisionId,
        String gender,
        LocalDate birthDate,
        Integer heightCm,
        String education,
        String occupation,
        String incomeRange,
        String city,
        byte[] wechatIdCiphertext,
        String wechatIdHmac,
        byte[] douyinIdCiphertext,
        String douyinIdHmac,
        byte[] douyinNicknameCiphertext,
        byte[] douyinProfileUrlCiphertext,
        List<FieldValue> dynamicFields,
        List<Photo> photos) {

    public GuestProfileSnapshot {
        wechatIdCiphertext = cloneOrNull(wechatIdCiphertext);
        douyinIdCiphertext = cloneOrNull(douyinIdCiphertext);
        douyinNicknameCiphertext = cloneOrNull(douyinNicknameCiphertext);
        douyinProfileUrlCiphertext = cloneOrNull(douyinProfileUrlCiphertext);
        dynamicFields = List.copyOf(dynamicFields);
        photos = List.copyOf(photos);
    }

    @Override
    public byte[] wechatIdCiphertext() {
        return cloneOrNull(wechatIdCiphertext);
    }

    @Override
    public byte[] douyinIdCiphertext() {
        return cloneOrNull(douyinIdCiphertext);
    }

    @Override
    public byte[] douyinNicknameCiphertext() {
        return cloneOrNull(douyinNicknameCiphertext);
    }

    @Override
    public byte[] douyinProfileUrlCiphertext() {
        return cloneOrNull(douyinProfileUrlCiphertext);
    }

    private static byte[] cloneOrNull(byte[] value) {
        return value == null ? null : value.clone();
    }

    public record FieldValue(
            String fieldCode,
            String fieldLabel,
            String dataType,
            String displayOption,
            String textValue,
            Long integerValue,
            BigDecimal decimalValue,
            LocalDate dateValue,
            Boolean booleanValue,
            String optionValue) {
    }

    public record Photo(
            String category,
            String objectKey,
            String sha256,
            long sizeBytes,
            String contentType,
            int width,
            int height,
            int sortOrder) {
    }
}
