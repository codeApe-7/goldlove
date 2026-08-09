package com.love.archive.review.web;

import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.PageView;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.identity.security.AuthLogics;
import com.love.archive.review.application.DeadlineFilter;
import com.love.archive.review.application.ProfileReviewDecisionView;
import com.love.archive.review.application.ProfileReviewDetail;
import com.love.archive.review.application.ProfileReviewFilter;
import com.love.archive.review.application.ProfileReviewListItem;
import com.love.archive.review.application.ProfileReviewService;
import com.love.archive.review.domain.RevisionStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/profile-reviews")
@RequiredArgsConstructor
public class AdminProfileReviewController {

    private final ProfileReviewService reviewService;
    private final AuthLogics authLogics;

    @GetMapping
    public ApiResponse<PageView<ProfileReviewListItem>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) RevisionStatus status,
            @RequestParam(required = false) DeadlineFilter deadline,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime submittedFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime submittedUntil,
            @RequestParam(required = false) UUID profileNo,
            HttpServletRequest request) {
        return ApiResponse.success(reviewService.search(
                new ProfileReviewFilter(
                        status, deadline, submittedFrom, submittedUntil, profileNo),
                page,
                size), RequestIdFilter.current(request));
    }

    @GetMapping("/{revisionId}")
    public ApiResponse<ProfileReviewDetail> detail(
            @PathVariable long revisionId,
            HttpServletRequest request) {
        return ApiResponse.success(
                reviewService.detail(revisionId), RequestIdFilter.current(request));
    }

    @PostMapping("/{revisionId}/approve")
    public ApiResponse<ProfileReviewDecisionView> approve(
            @PathVariable long revisionId,
            @Valid @RequestBody ReviewDecisionRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(reviewService.approve(
                authLogics.admin().getLoginIdAsLong(),
                revisionId,
                body.expectedVersion(),
                RequestIdFilter.current(request)), RequestIdFilter.current(request));
    }

    @PostMapping("/{revisionId}/reject")
    public ApiResponse<ProfileReviewDecisionView> reject(
            @PathVariable long revisionId,
            @Valid @RequestBody ReviewDecisionRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(reviewService.reject(
                authLogics.admin().getLoginIdAsLong(),
                revisionId,
                body.expectedVersion(),
                body.reasonCode(),
                body.comment(),
                RequestIdFilter.current(request)), RequestIdFilter.current(request));
    }
}
