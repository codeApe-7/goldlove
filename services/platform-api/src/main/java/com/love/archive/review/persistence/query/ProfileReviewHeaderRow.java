package com.love.archive.review.persistence.query;

import com.love.archive.review.domain.RevisionStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProfileReviewHeaderRow(
        long revisionId,
        long guestProfileId,
        int revisionNumber,
        RevisionStatus status,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewDeadlineAt,
        OffsetDateTime reviewedAt,
        long version,
        UUID profileNo,
        Long currentApprovedRevisionId) {
}
