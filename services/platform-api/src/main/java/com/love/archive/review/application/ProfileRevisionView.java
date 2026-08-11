package com.love.archive.review.application;

import com.love.archive.review.domain.RevisionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ProfileRevisionView(
        long id,
        int revisionNumber,
        RevisionStatus status,
        String gender,
        LocalDate birthDate,
        Integer heightCm,
        String education,
        String occupation,
        String incomeRange,
        String city,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewDeadlineAt,
        OffsetDateTime reviewedAt,
        long version,
        List<FieldValue> dynamicFields,
        List<Photo> photos) {

    public ProfileRevisionView {
        dynamicFields = List.copyOf(dynamicFields);
        photos = List.copyOf(photos);
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
            int sortOrder,
            String downloadUrl) {
    }
}
