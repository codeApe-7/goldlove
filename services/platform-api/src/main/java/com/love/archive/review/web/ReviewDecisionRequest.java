package com.love.archive.review.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewDecisionRequest(
        @NotNull Long expectedVersion,
        @Size(max = 64) String reasonCode,
        @Size(max = 1000) String comment) {
}
