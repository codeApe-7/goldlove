package com.love.archive.review.application;

import com.love.archive.review.domain.RevisionStatus;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProfileReviewDetail(
        UUID profileNo,
        long revisionId,
        int revisionNumber,
        RevisionStatus status,
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
        OffsetDateTime submittedAt,
        OffsetDateTime reviewDeadlineAt,
        OffsetDateTime reviewedAt,
        long version,
        List<ProfileRevisionView.FieldValue> dynamicFields,
        Long lastApprovedRevisionId,
        List<ProfileRevisionView.Photo> photos,
        List<ProfileFieldDifference> differences) {

    public ProfileReviewDetail {
        dynamicFields = List.copyOf(dynamicFields);
        photos = List.copyOf(photos);
        differences = List.copyOf(differences);
    }
}
