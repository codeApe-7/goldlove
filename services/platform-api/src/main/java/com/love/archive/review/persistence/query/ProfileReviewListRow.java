package com.love.archive.review.persistence.query;

import com.love.archive.review.domain.RevisionStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProfileReviewListRow(
        long revisionId,
        int revisionNumber,
        RevisionStatus status,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewDeadlineAt,
        UUID profileNo,
        Long currentApprovedRevisionId) {
}
