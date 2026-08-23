package com.love.archive.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.course.domain.VideoUploadStatus;
import com.love.archive.course.persistence.CourseVideoUploadEntity;
import com.love.archive.course.persistence.CourseVideoUploadMapper;
import com.love.archive.storage.application.MultipartUploadHandle;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.storage.application.PartRef;
import com.love.archive.storage.application.StoredObjectView;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课程素材：插图 / 封面、{@code .md} 正文文件、视频分块上传。
 *
 * <p>视频走「浏览器切片 → 后端中转 → COS 分块上传」。选这条路是因为它不需要
 * 桶 CORS 与 STS 临时凭据，COS 密钥不出服务端；代价是文件流量过一遍后端，
 * 对管理员侧的低频操作可以接受。</p>
 */
@Service
@RequiredArgsConstructor
public class CourseAssetService {

    private static final Duration PREVIEW_TTL = Duration.ofMinutes(30);

    /** 与 {@code SaveCourseRequest} 的正文上限一致。 */
    private static final int MAX_MARKDOWN_BYTES = 1024 * 1024;

    /** 视频类型白名单。浏览器能直接播的就这几种。 */
    private static final Set<String> VIDEO_CONTENT_TYPES =
            Set.of("video/mp4", "video/quicktime", "video/webm");

    private final CourseVideoUploadMapper uploadMapper;
    private final ObjectStorageService storageService;
    private final CourseImageValidator imageValidator;

    /** 插图与封面共用这一个入口：两者的处理完全一样，没必要分两个端点。 */
    public CourseImageUploadView uploadImage(byte[] content) {
        String contentType = imageValidator.validate(content);
        String objectKey = imageKey(contentType);
        StoredObjectView stored = storageService.put(objectKey, content, contentType);
        return new CourseImageUploadView(
                stored.objectKey(),
                storageService.signDownloadUrl(stored.objectKey(), PREVIEW_TTL),
                stored.sizeBytes(),
                stored.contentType());
    }

    /**
     * 把上传的 {@code .md} 读成文本回给编辑器。
     *
     * <p>刻意**不落库**：管理员应该先在编辑器里看到内容、有机会改，再随保存课程入库。</p>
     *
     * <p>用严格模式解码 UTF-8 而不是 {@code new String(bytes, UTF_8)}——后者遇到非法字节
     * 会静默替换成 U+FFFD，于是一份 GBK 的文件会「成功」导入成一堆乱码问号，
     * 管理员还以为是编辑器的问题。</p>
     */
    public MarkdownUploadView readMarkdown(byte[] content) {
        if (content == null || content.length == 0) {
            throw CourseErrors.markdownInvalid();
        }
        if (content.length > MAX_MARKDOWN_BYTES) {
            throw CourseErrors.markdownTooLarge();
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(content));
            return new MarkdownUploadView(stripBom(decoded.toString()), content.length);
        } catch (CharacterCodingException notUtf8) {
            throw CourseErrors.markdownInvalid();
        }
    }

    @Transactional
    public VideoUploadSessionView initiateVideoUpload(
            String filename, String contentType, Long declaredTotalBytes, long adminId) {
        String normalizedType = contentType == null ? "" : contentType.trim().toLowerCase();
        if (!VIDEO_CONTENT_TYPES.contains(normalizedType)) {
            throw CourseErrors.videoTypeUnsupported();
        }
        if (declaredTotalBytes != null
                && declaredTotalBytes > ObjectStorageService.MAX_VIDEO_BYTES) {
            throw CourseErrors.videoTooLarge();
        }

        String objectKey = videoKey(normalizedType);
        MultipartUploadHandle handle =
                storageService.initiateMultipartUpload(objectKey, normalizedType);

        OffsetDateTime now = OffsetDateTime.now();
        CourseVideoUploadEntity session = new CourseVideoUploadEntity();
        session.setUploadId(handle.uploadId());
        session.setObjectKey(handle.objectKey());
        session.setAdminId(adminId);
        session.setOriginalFilename(trimFilename(filename));
        session.setContentType(normalizedType);
        session.setStatus(VideoUploadStatus.IN_PROGRESS);
        session.setDeclaredTotalBytes(declaredTotalBytes);
        session.setUploadedBytes(0L);
        session.setPartCount(0);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        uploadMapper.insert(session);

        return new VideoUploadSessionView(
                handle.uploadId(), handle.objectKey(), ObjectStorageService.MAX_PART_BYTES);
    }

    @Transactional
    public VideoPartView uploadPart(String uploadId, int partNumber, byte[] content, long adminId) {
        CourseVideoUploadEntity session = requireOwnSession(uploadId, adminId);
        String etag = storageService.uploadPart(
                session.getUploadId(), session.getObjectKey(), partNumber, content);

        // 只是进度统计，不作为校验依据——分块可以重传，累加值会偏大。
        session.setUploadedBytes(
                (session.getUploadedBytes() == null ? 0L : session.getUploadedBytes())
                        + content.length);
        session.setPartCount(
                Math.max(session.getPartCount() == null ? 0 : session.getPartCount(), partNumber));
        session.setUpdatedAt(OffsetDateTime.now());
        uploadMapper.updateById(session);

        return new VideoPartView(partNumber, etag);
    }

    @Transactional
    public VideoUploadResultView completeVideoUpload(
            String uploadId, List<PartRef> parts, long adminId) {
        CourseVideoUploadEntity session = requireOwnSession(uploadId, adminId);
        StoredObjectView stored = storageService.completeMultipartUpload(
                session.getUploadId(), session.getObjectKey(), parts);

        session.setStatus(VideoUploadStatus.COMPLETED);
        session.setUploadedBytes(stored.sizeBytes());
        session.setUpdatedAt(OffsetDateTime.now());
        uploadMapper.updateById(session);

        return new VideoUploadResultView(
                stored.objectKey(),
                stored.sizeBytes(),
                stored.contentType(),
                // 让管理员保存之前就能回放确认传对了文件，而不是先存再回头检查。
                storageService.signDownloadUrl(stored.objectKey(), PREVIEW_TTL));
    }

    @Transactional
    public void abortVideoUpload(String uploadId, long adminId) {
        CourseVideoUploadEntity session = requireOwnSession(uploadId, adminId);
        storageService.abortMultipartUpload(session.getUploadId(), session.getObjectKey());
        session.setStatus(VideoUploadStatus.ABORTED);
        session.setUpdatedAt(OffsetDateTime.now());
        uploadMapper.updateById(session);
    }

    /**
     * 按 uploadId **和管理员** 找进行中的会话。
     *
     * <p>只按 uploadId 找是个漏洞：uploadId 随初始化响应回到浏览器，
     * 任何已登录管理员拿到别人的 uploadId 就能往那个上传里塞分块或提前 complete。</p>
     */
    private CourseVideoUploadEntity requireOwnSession(String uploadId, long adminId) {
        CourseVideoUploadEntity session = uploadMapper.selectOne(
                Wrappers.<CourseVideoUploadEntity>lambdaQuery()
                        .eq(CourseVideoUploadEntity::getUploadId, uploadId)
                        .eq(CourseVideoUploadEntity::getAdminId, adminId)
                        .eq(CourseVideoUploadEntity::getStatus, VideoUploadStatus.IN_PROGRESS));
        if (session == null) {
            throw CourseErrors.uploadNotFound();
        }
        return session;
    }

    private static String imageKey(String contentType) {
        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "webp";
        };
        return "courses/images/" + UUID.randomUUID() + "." + extension;
    }

    private static String videoKey(String contentType) {
        String extension = switch (contentType) {
            case "video/quicktime" -> "mov";
            case "video/webm" -> "webm";
            default -> "mp4";
        };
        return "courses/videos/" + UUID.randomUUID() + "." + extension;
    }

    /** 原始文件名只用于后台显示「我传的是哪个文件」，不参与对象键。 */
    private static String trimFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String trimmed = filename.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    /** Windows 上导出的 md 常带 BOM，留着会让第一个标题渲染不出来。 */
    private static String stripBom(String text) {
        return text.startsWith("﻿") ? text.substring(1) : text;
    }
}
