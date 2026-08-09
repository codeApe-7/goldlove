package com.love.archive.review.web;

import com.love.archive.common.security.GuestAccountIdentity;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.review.application.ProfileRevisionView;
import com.love.archive.review.application.ProfileSubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guest/profile")
@RequiredArgsConstructor
public class GuestProfileSubmissionController {

    private final ProfileSubmissionService submissionService;
    private final GuestAccountIdentity guestIdentity;

    @PostMapping("/submissions")
    public ApiResponse<ProfileRevisionView> submit(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        ProfileRevisionView submitted = submissionService.submit(
                guestIdentity.currentGuestAccountId(),
                idempotencyKey,
                RequestIdFilter.current(request));
        return ApiResponse.success(submitted, RequestIdFilter.current(request));
    }

    @GetMapping("/revisions/{revisionId}")
    public ApiResponse<ProfileRevisionView> revision(
            @PathVariable long revisionId,
            HttpServletRequest request) {
        ProfileRevisionView revision = submissionService.getOwned(
                guestIdentity.currentGuestAccountId(), revisionId);
        return ApiResponse.success(revision, RequestIdFilter.current(request));
    }
}
