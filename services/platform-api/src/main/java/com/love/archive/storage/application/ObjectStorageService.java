package com.love.archive.storage.application;

import com.love.archive.common.web.ApiException;
import com.love.archive.storage.config.CosStorageProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.AbortMultipartUploadRequest;
import com.qcloud.cos.model.CompleteMultipartUploadRequest;
import com.qcloud.cos.model.InitiateMultipartUploadRequest;
import com.qcloud.cos.model.InitiateMultipartUploadResult;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PartETag;
import com.qcloud.cos.model.UploadPartRequest;
import com.qcloud.cos.model.UploadPartResult;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ObjectStorageService {

    private static final Pattern OBJECT_KEY =
            Pattern.compile("[0-9a-zA-Z][0-9a-zA-Z._/-]{0,1023}");
    private static final long MAX_OBJECT_BYTES = 10L * 1024 * 1024;

    /**
     * 分块上传的限额，与 {@link #put} 的 10 MiB 刻意不共用常量。
     *
     * <p>{@code put} 是给照片设计的：整个文件读进内存，10 MiB 是合适的天花板。
     * 视频动辄上百 MB，走的是「浏览器切片 → 后端中转 → COS 分块上传」，
     * 一次请求只经手一块。</p>
     *
     * <p>{@link #MAX_PART_BYTES} 定在 8 MiB 是为了压在
     * {@code spring.servlet.multipart.max-file-size}（10MB）之下——这样浏览器的分块
     * 直接走普通 multipart 表单就行，不必为了传视频去放宽全局 multipart 上限。</p>
     *
     * <p>{@link #MIN_PART_BYTES} 是 COS 对「非末块」的下限，只作为常量导出给调用方切片用。
     * {@link #uploadPart} **不校验它**：单看一次请求判断不出这是不是末块，
     * 而末块本来就允许小于 1 MiB。真正违规时由 COS 在 complete 时报错。</p>
     */
    public static final int MIN_PART_BYTES = 1024 * 1024;

    public static final int MAX_PART_BYTES = 8 * 1024 * 1024;

    /** COS 的分块数上限。 */
    public static final int MAX_PARTS = 10_000;

    /** 单个视频的总量防呆：15 分钟的课再怎么高码率也到不了 2 GiB。 */
    public static final long MAX_VIDEO_BYTES = 2L * 1024 * 1024 * 1024;


    private final ObjectProvider<COSClient> clientProvider;
    private final CosStorageProperties properties;

    public ObjectStorageService(
            ObjectProvider<COSClient> clientProvider,
            CosStorageProperties properties) {
        this.clientProvider = clientProvider;
        this.properties = properties;
    }

    public StoredObjectView put(String objectKey, byte[] content, String contentType) {
        COSClient client = requireClient();
        String normalizedKey = requireKey(objectKey);
        if (content == null || content.length == 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "OBJECT_STORAGE_CONTENT_INVALID", "文件内容不能为空");
        }
        if (content.length > MAX_OBJECT_BYTES) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "OBJECT_STORAGE_OBJECT_TOO_LARGE",
                    "文件不能超过 10 MiB");
        }
        requireContentType(contentType);

        String sha256 = sha256(content);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(contentType);
        metadata.addUserMetadata("sha256", sha256);
        try {
            client.putObject(
                    properties.bucket(), normalizedKey,
                    new ByteArrayInputStream(content), metadata);
            return new StoredObjectView(
                    normalizedKey, properties.bucket(), content.length, contentType, sha256);
        } catch (CosClientException exception) {
            throw storageFailure("上传对象失败");
        }
    }

    public boolean exists(String objectKey) {
        COSClient client = requireClient();
        String normalizedKey = requireKey(objectKey);
        try {
            return client.doesObjectExist(properties.bucket(), normalizedKey);
        } catch (CosClientException exception) {
            throw storageFailure("查询对象失败");
        }
    }

    public String signDownloadUrl(String objectKey, Duration ttl) {
        COSClient client = requireClient();
        String normalizedKey = requireKey(objectKey);
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.toMinutes() > 7 * 24 * 60) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "OBJECT_STORAGE_URL_TTL_INVALID", "URL 有效期不正确");
        }
        Date expiration = Date.from(Instant.now().plus(ttl));
        try {
            return client.generatePresignedUrl(properties.bucket(), normalizedKey, expiration)
                    .toString();
        } catch (CosClientException exception) {
            throw storageFailure("生成下载地址失败");
        }
    }

    public void delete(String objectKey) {
        COSClient client = requireClient();
        String normalizedKey = requireKey(objectKey);
        try {
            client.deleteObject(properties.bucket(), normalizedKey);
        } catch (CosClientException exception) {
            throw storageFailure("删除对象失败");
        }
    }

    /**
     * 开一个分块上传会话。COS 在这一步就要拿到 Content-Type——
     * 之后每一块都只是字节流，最终对象的类型取自这里。
     */
    public MultipartUploadHandle initiateMultipartUpload(String objectKey, String contentType) {
        COSClient client = requireClient();
        String normalizedKey = requireKey(objectKey);
        requireContentType(contentType);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        try {
            InitiateMultipartUploadResult result = client.initiateMultipartUpload(
                    new InitiateMultipartUploadRequest(
                            properties.bucket(), normalizedKey, metadata));
            return new MultipartUploadHandle(result.getUploadId(), normalizedKey);
        } catch (CosClientException exception) {
            throw storageFailure("创建分块上传会话失败");
        }
    }

    /**
     * 上传一块，返回 COS 给的 ETag。调用方要把它记住，complete 时全部交回去。
     *
     * <p>这里**不校验 {@link #MIN_PART_BYTES}**：末块允许小于 1 MiB，
     * 而单看一次请求判断不出这是不是末块。</p>
     */
    public String uploadPart(String uploadId, String objectKey, int partNumber, byte[] content) {
        COSClient client = requireClient();
        String normalizedKey = requireKey(objectKey);
        String normalizedUploadId = requireUploadId(uploadId);
        requirePartNumber(partNumber);
        if (content == null || content.length == 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "OBJECT_STORAGE_CONTENT_INVALID", "分块内容不能为空");
        }
        if (content.length > MAX_PART_BYTES) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "OBJECT_STORAGE_PART_TOO_LARGE",
                    "单个分块不能超过 8 MiB");
        }

        UploadPartRequest request = new UploadPartRequest();
        request.setBucketName(properties.bucket());
        request.setKey(normalizedKey);
        request.setUploadId(normalizedUploadId);
        request.setPartNumber(partNumber);
        request.setPartSize(content.length);
        request.setInputStream(new ByteArrayInputStream(content));
        try {
            UploadPartResult result = client.uploadPart(request);
            return result.getETag();
        } catch (CosClientException exception) {
            throw storageFailure("上传分块失败");
        }
    }

    /**
     * 把全部分块拼成一个对象。
     *
     * <p>返回值里的 {@code sha256} 是 {@code null}：分块上传时服务端从没同时握有整个文件，
     * 算不出摘要，如实留空而不是编一个。大小与类型回头问 COS 要——
     * 以对象存储实际存下的为准，不用客户端声明的数字。</p>
     */
    public StoredObjectView completeMultipartUpload(
            String uploadId, String objectKey, List<PartRef> parts) {
        COSClient client = requireClient();
        String normalizedKey = requireKey(objectKey);
        String normalizedUploadId = requireUploadId(uploadId);
        List<PartETag> partETags = toPartETags(parts);

        try {
            client.completeMultipartUpload(new CompleteMultipartUploadRequest(
                    properties.bucket(), normalizedKey, normalizedUploadId, partETags));
            ObjectMetadata stored = client.getObjectMetadata(properties.bucket(), normalizedKey);
            return new StoredObjectView(
                    normalizedKey,
                    properties.bucket(),
                    stored.getContentLength(),
                    stored.getContentType(),
                    null);
        } catch (CosClientException exception) {
            throw storageFailure("合并分块失败");
        }
    }

    /**
     * 中止会话并让 COS 丢掉已收的分块。
     *
     * <p>这是清理路径，所以 COS 的失败被咽掉：会话在 COS 那边已经不存在时硬抛异常，
     * 只会让调用方拿着一条删不掉的上传记录反复重试。</p>
     */
    public void abortMultipartUpload(String uploadId, String objectKey) {
        COSClient client = requireClient();
        String normalizedKey = requireKey(objectKey);
        String normalizedUploadId = requireUploadId(uploadId);
        try {
            client.abortMultipartUpload(new AbortMultipartUploadRequest(
                    properties.bucket(), normalizedKey, normalizedUploadId));
        } catch (CosClientException exception) {
            // 已经没有这个会话了，对调用方来说目的已达成。
        }
    }

    private COSClient requireClient() {
        COSClient client = clientProvider.getIfAvailable();
        if (client == null) {
            throw notConfigured();
        }
        return client;
    }

    private static ApiException notConfigured() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OBJECT_STORAGE_NOT_CONFIGURED",
                "对象存储尚未配置");
    }

    private static String requireKey(String objectKey) {
        if (!StringUtils.hasText(objectKey) || !OBJECT_KEY.matcher(objectKey).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "OBJECT_STORAGE_KEY_INVALID", "对象键格式不正确");
        }
        return objectKey;
    }

    private static void requireContentType(String contentType) {
        if (!StringUtils.hasText(contentType) || contentType.length() > 255) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "OBJECT_STORAGE_CONTENT_TYPE_INVALID", "文件类型不正确");
        }
    }

    /** uploadId 由 COS 生成，长度对齐 {@code course_video_upload.upload_id} 的 256。 */
    private static String requireUploadId(String uploadId) {
        if (!StringUtils.hasText(uploadId) || uploadId.length() > 256) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "OBJECT_STORAGE_UPLOAD_ID_INVALID",
                    "上传会话标识不正确");
        }
        return uploadId;
    }

    /** COS 的块号从 1 开始。 */
    private static void requirePartNumber(int partNumber) {
        if (partNumber < 1 || partNumber > MAX_PARTS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "OBJECT_STORAGE_PART_NUMBER_INVALID", "分块号不正确");
        }
    }

    /**
     * 校验并排序分块清单。COS 要求按块号升序提交，顺序错了对象拼不出来；
     * 块号重复说明调用方把某一块传了两次，此时哪个 ETag 有效无从判断，直接拒绝。
     */
    private static List<PartETag> toPartETags(List<PartRef> parts) {
        if (parts == null || parts.isEmpty()) {
            throw partsInvalid();
        }
        Set<Integer> seen = new HashSet<>();
        for (PartRef part : parts) {
            if (part == null) {
                throw partsInvalid();
            }
            requirePartNumber(part.partNumber());
            if (!StringUtils.hasText(part.etag())) {
                throw partsInvalid();
            }
            if (!seen.add(part.partNumber())) {
                throw partsInvalid();
            }
        }
        List<PartETag> sorted = new ArrayList<>(parts.size());
        parts.stream()
                .sorted(Comparator.comparingInt(PartRef::partNumber))
                .forEach(part -> sorted.add(new PartETag(part.partNumber(), part.etag())));
        return sorted;
    }

    private static ApiException partsInvalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "OBJECT_STORAGE_PARTS_INVALID", "分块清单不正确");
    }


    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static ApiException storageFailure(String message) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY, "OBJECT_STORAGE_OPERATION_FAILED", message);
    }
}
