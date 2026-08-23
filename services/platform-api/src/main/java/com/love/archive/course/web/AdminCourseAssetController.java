package com.love.archive.course.web;

import com.love.archive.common.security.AdminIdentity;
import com.love.archive.common.web.ApiException;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.course.application.CourseAssetService;
import com.love.archive.course.application.CourseImageUploadView;
import com.love.archive.course.application.MarkdownUploadView;
import com.love.archive.course.application.VideoPartView;
import com.love.archive.course.application.VideoUploadResultView;
import com.love.archive.course.application.VideoUploadSessionView;
import com.love.archive.storage.application.PartRef;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 课程素材上传。
 *
 * <p>视频是分三步：{@code POST /videos/uploads} 开会话 → 逐块
 * {@code POST /videos/uploads/{uploadId}/parts} → {@code POST .../complete} 合并。
 * 中途放弃调 {@code DELETE .../{uploadId}}，否则 COS 会一直留着半截分块。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/course-assets")
@RequiredArgsConstructor
public class AdminCourseAssetController {

    private final CourseAssetService assetService;
    private final AdminIdentity adminIdentity;

    /** 封面与正文插图共用。 */
    @PostMapping("/images")
    public ApiResponse<CourseImageUploadView> uploadImage(
            @RequestParam("file") MultipartFile file, HttpServletRequest request) {
        return ApiResponse.success(
                assetService.uploadImage(bytesOf(file, "图片内容不能为空")),
                RequestIdFilter.current(request));
    }

    /** 读 {@code .md} 回给编辑器，不落库。 */
    @PostMapping("/markdown")
    public ApiResponse<MarkdownUploadView> uploadMarkdown(
            @RequestParam("file") MultipartFile file, HttpServletRequest request) {
        return ApiResponse.success(
                assetService.readMarkdown(bytesOf(file, "Markdown 内容不能为空")),
                RequestIdFilter.current(request));
    }

    @PostMapping("/videos/uploads")
    public ApiResponse<VideoUploadSessionView> initiateVideoUpload(
            @Valid @RequestBody InitiateVideoUploadRequest body, HttpServletRequest request) {
        return ApiResponse.success(
                assetService.initiateVideoUpload(
                        body.filename(),
                        body.contentType(),
                        body.totalBytes(),
                        adminIdentity.currentAdminId()),
                RequestIdFilter.current(request));
    }

    @PostMapping("/videos/uploads/{uploadId}/parts")
    public ApiResponse<VideoPartView> uploadPart(
            @PathVariable String uploadId,
            @RequestParam("partNumber") int partNumber,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        return ApiResponse.success(
                assetService.uploadPart(
                        uploadId,
                        partNumber,
                        bytesOf(file, "分块内容不能为空"),
                        adminIdentity.currentAdminId()),
                RequestIdFilter.current(request));
    }

    @PostMapping("/videos/uploads/{uploadId}/complete")
    public ApiResponse<VideoUploadResultView> completeVideoUpload(
            @PathVariable String uploadId,
            @Valid @RequestBody CompleteVideoUploadRequest body,
            HttpServletRequest request) {
        List<PartRef> parts = body.parts().stream()
                .map(part -> new PartRef(part.partNumber(), part.etag()))
                .toList();
        return ApiResponse.success(
                assetService.completeVideoUpload(uploadId, parts, adminIdentity.currentAdminId()),
                RequestIdFilter.current(request));
    }

    @DeleteMapping("/videos/uploads/{uploadId}")
    public ApiResponse<Void> abortVideoUpload(
            @PathVariable String uploadId, HttpServletRequest request) {
        assetService.abortVideoUpload(uploadId, adminIdentity.currentAdminId());
        return ApiResponse.success(null, RequestIdFilter.current(request));
    }

    private static byte[] bytesOf(MultipartFile file, String message) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COURSE_ASSET_CONTENT_INVALID", message);
        }
    }
}
