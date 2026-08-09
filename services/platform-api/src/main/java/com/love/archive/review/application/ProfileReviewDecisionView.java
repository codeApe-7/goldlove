package com.love.archive.review.application;

import com.love.archive.review.domain.RevisionStatus;
import java.time.OffsetDateTime;

public record ProfileReviewDecisionView(
        long id,
        int revisionNumber,
        RevisionStatus status,
        OffsetDateTime reviewedAt,
        long version) {
}
