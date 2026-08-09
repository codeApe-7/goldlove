package com.love.archive.storage.application;

import com.love.archive.common.web.ApiException;
import com.love.archive.storage.config.CosStorageProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.ObjectMetadata;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
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
        if (!StringUtils.hasText(contentType) || contentType.length() > 255) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "OBJECT_STORAGE_CONTENT_TYPE_INVALID", "文件类型不正确");
        }

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
