package com.love.archive.review.application;

import com.love.archive.review.domain.RevisionStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProfileReviewFilter(
        RevisionStatus status,
        DeadlineFilter deadline,
        OffsetDateTime submittedFrom,
        OffsetDateTime submittedUntil,
        UUID profileNo) {
}
