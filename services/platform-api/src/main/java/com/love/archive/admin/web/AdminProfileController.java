package com.love.archive.admin.web;

import com.love.archive.admin.application.AdminProfileCounts;
import com.love.archive.admin.application.AdminProfileDetail;
import com.love.archive.admin.application.AdminProfileExportService;
import com.love.archive.admin.application.AdminProfileFilter;
import com.love.archive.admin.application.AdminProfileListItem;
import com.love.archive.admin.application.AdminProfileQueryService;
import com.love.archive.admin.application.AdminProfileSort;
import com.love.archive.admin.application.ProfileExportFile;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.PageView;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.identity.security.AuthLogics;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/profiles")
@RequiredArgsConstructor
public class AdminProfileController {

    private final AdminProfileQueryService profileQueryService;
    private final AdminProfileExportService profileExportService;
    private final AuthLogics authLogics;

    @GetMapping
    public ApiResponse<PageView<AdminProfileListItem>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String accountStatus,
            @RequestParam(required = false) String membershipTier,
            @RequestParam(required = false) Boolean paidOnly,
            @RequestParam(required = false) String city,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            @RequestParam(required = false) AdminProfileSort sort,
            HttpServletRequest request) {
        PageView<AdminProfileListItem> result = profileQueryService.list(
                filterOf(keyword, status, accountStatus, membershipTier, paidOnly, city,
                        createdFrom, createdTo, sort),
                page,
                size);
        return ApiResponse.success(result, RequestIdFilter.current(request));
    }

    /** tab 上的数量。与列表用同一套筛选参数，但会忽略 status / accountStatus / paidOnly。 */
    @GetMapping("/counts")
    public ApiResponse<AdminProfileCounts> counts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String membershipTier,
            @RequestParam(required = false) String city,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            HttpServletRequest request) {
        AdminProfileCounts counts = profileQueryService.counts(
                filterOf(keyword, null, null, membershipTier, null, city,
                        createdFrom, createdTo, null));
        return ApiResponse.success(counts, RequestIdFilter.current(request));
    }

    /**
     * 导出 CSV。传 ids 时只导这些行（列表勾选），不传则导出当前筛选的全量，
     * 两种情况都受 {@link AdminProfileExportService#MAX_EXPORT_ROWS} 限制。
     */
    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String accountStatus,
            @RequestParam(required = false) String membershipTier,
            @RequestParam(required = false) Boolean paidOnly,
            @RequestParam(required = false) String city,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            @RequestParam(required = false) AdminProfileSort sort,
            @RequestParam(required = false) List<Long> ids,
            HttpServletRequest request) {
        ProfileExportFile file = profileExportService.export(
                filterOf(keyword, status, accountStatus, membershipTier, paidOnly, city,
                        createdFrom, createdTo, sort),
                ids,
                authLogics.admin().getLoginIdAsLong(),
                RequestIdFilter.current(request));
        byte[] body = file.csv().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .contentLength(body.length)
                .body(new ByteArrayResource(body));
    }

    @GetMapping("/{profileId}")
    public ApiResponse<AdminProfileDetail> detail(
            @PathVariable long profileId,
            HttpServletRequest request) {
        return ApiResponse.success(
                profileQueryService.detail(profileId), RequestIdFilter.current(request));
    }

    private static AdminProfileFilter filterOf(
            String keyword,
            String status,
            String accountStatus,
            String membershipTier,
            Boolean paidOnly,
            String city,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            AdminProfileSort sort) {
        return AdminProfileFilter.of(
                keyword, status, accountStatus, membershipTier, paidOnly, city,
                createdFrom, createdTo, sort);
    }
}
