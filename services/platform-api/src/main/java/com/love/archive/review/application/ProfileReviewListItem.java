package com.love.archive.review.application;

import com.love.archive.review.domain.RevisionStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProfileReviewListItem(
        long revisionId,
        int revisionNumber,
        RevisionStatus status,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewDeadlineAt,
        UUID profileNo,
        Long currentApprovedRevisionId) {
}
