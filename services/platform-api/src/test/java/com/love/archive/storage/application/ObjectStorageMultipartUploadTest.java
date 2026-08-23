package com.love.archive.storage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.love.archive.common.web.ApiException;
import com.love.archive.storage.config.CosStorageProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.AbortMultipartUploadRequest;
import com.qcloud.cos.model.CompleteMultipartUploadRequest;
import com.qcloud.cos.model.CompleteMultipartUploadResult;
import com.qcloud.cos.model.InitiateMultipartUploadRequest;
import com.qcloud.cos.model.InitiateMultipartUploadResult;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.UploadPartRequest;
import com.qcloud.cos.model.UploadPartResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * COS 分块上传。视频不走 {@code put()}：那条路径把整个文件读进内存并钉在 10 MiB，
 * 是给照片设计的。分块上传有自己一套限额，两者刻意不共用常量。
 */
class ObjectStorageMultipartUploadTest {

    private static final String BUCKET = "loveplatform-1314980040";
    private static final String KEY = "courses/videos/lesson.mp4";
    private static final String UPLOAD_ID = "cos-upload-id-0001";

    private COSClient client;
    private ObjectProvider<COSClient> clientProvider;
    private ObjectStorageService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void createServiceWithMockedClient() {
        client = mock(COSClient.class);
        clientProvider = mock(ObjectProvider.class);
        when(clientProvider.getIfAvailable()).thenReturn(client);
        service = new ObjectStorageService(
                clientProvider,
                new CosStorageProperties("secret-id", "secret-key", "ap-guangzhou", BUCKET));
    }

    @Test
    void initiateReturnsTheUploadIdAndCarriesTheDeclaredContentType() {
        InitiateMultipartUploadResult result = new InitiateMultipartUploadResult();
        result.setUploadId(UPLOAD_ID);
        when(client.initiateMultipartUpload(any(InitiateMultipartUploadRequest.class)))
                .thenReturn(result);

        MultipartUploadHandle handle = service.initiateMultipartUpload(KEY, "video/mp4");

        assertThat(handle.uploadId()).isEqualTo(UPLOAD_ID);
        assertThat(handle.objectKey()).isEqualTo(KEY);
        ArgumentCaptor<InitiateMultipartUploadRequest> request =
                ArgumentCaptor.forClass(InitiateMultipartUploadRequest.class);
        verify(client).initiateMultipartUpload(request.capture());
        assertThat(request.getValue().getBucketName()).isEqualTo(BUCKET);
        assertThat(request.getValue().getKey()).isEqualTo(KEY);
        assertThat(request.getValue().getObjectMetadata().getContentType()).isEqualTo("video/mp4");
    }

    @Test
    void uploadPartSendsTheExactBytesAndReturnsTheEtag() {
        UploadPartResult result = new UploadPartResult();
        result.setPartNumber(2);
        result.setETag("etag-of-part-2");
        when(client.uploadPart(any(UploadPartRequest.class))).thenReturn(result);
        byte[] content = new byte[ObjectStorageService.MIN_PART_BYTES];

        String etag = service.uploadPart(UPLOAD_ID, KEY, 2, content);

        assertThat(etag).isEqualTo("etag-of-part-2");
        ArgumentCaptor<UploadPartRequest> request =
                ArgumentCaptor.forClass(UploadPartRequest.class);
        verify(client).uploadPart(request.capture());
        assertThat(request.getValue().getUploadId()).isEqualTo(UPLOAD_ID);
        assertThat(request.getValue().getKey()).isEqualTo(KEY);
        assertThat(request.getValue().getPartNumber()).isEqualTo(2);
        assertThat(request.getValue().getPartSize()).isEqualTo(content.length);
    }

    @Test
    void rejectsPartNumbersOutsideTheCosRange() {
        byte[] content = new byte[ObjectStorageService.MIN_PART_BYTES];

        assertCode(() -> service.uploadPart(UPLOAD_ID, KEY, 0, content),
                "OBJECT_STORAGE_PART_NUMBER_INVALID");
        assertCode(() -> service.uploadPart(UPLOAD_ID, KEY, -1, content),
                "OBJECT_STORAGE_PART_NUMBER_INVALID");
        assertCode(
                () -> service.uploadPart(
                        UPLOAD_ID, KEY, ObjectStorageService.MAX_PARTS + 1, content),
                "OBJECT_STORAGE_PART_NUMBER_INVALID");
        // 一次都没打到 COS：越界的分块号连请求都不该发出去。
        verifyNoInteractions(client);
    }

    @Test
    void rejectsEmptyAndOversizedParts() {
        assertCode(() -> service.uploadPart(UPLOAD_ID, KEY, 1, new byte[0]),
                "OBJECT_STORAGE_CONTENT_INVALID");
        assertCode(() -> service.uploadPart(UPLOAD_ID, KEY, 1, null),
                "OBJECT_STORAGE_CONTENT_INVALID");
        assertCode(
                () -> service.uploadPart(
                        UPLOAD_ID, KEY, 1, new byte[ObjectStorageService.MAX_PART_BYTES + 1]),
                "OBJECT_STORAGE_PART_TOO_LARGE");
    }

    /**
     * 分块上限刻意压在 {@code spring.servlet.multipart.max-file-size}（10MB）之下，
     * 这样浏览器切片直接走普通 multipart 表单就行，不用改 multipart 配置。
     */
    @Test
    void partCeilingStaysBelowTheServletMultipartLimit() {
        assertThat(ObjectStorageService.MAX_PART_BYTES).isEqualTo(8 * 1024 * 1024);
        assertThat(ObjectStorageService.MAX_PART_BYTES).isLessThan(10 * 1024 * 1024);
        assertThat(ObjectStorageService.MIN_PART_BYTES).isEqualTo(1024 * 1024);
        assertThat(ObjectStorageService.MAX_PARTS).isEqualTo(10_000);
        assertThat(ObjectStorageService.MAX_VIDEO_BYTES).isEqualTo(2L * 1024 * 1024 * 1024);
    }

    @Test
    void completeAssemblesThePartsAndReportsWhatCosActuallyStored() {
        when(client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(new CompleteMultipartUploadResult());
        ObjectMetadata stored = new ObjectMetadata();
        stored.setContentLength(12_582_912L);
        stored.setContentType("video/mp4");
        when(client.getObjectMetadata(BUCKET, KEY)).thenReturn(stored);

        StoredObjectView view = service.completeMultipartUpload(
                UPLOAD_ID, KEY, List.of(new PartRef(1, "etag-1"), new PartRef(2, "etag-2")));

        assertThat(view.objectKey()).isEqualTo(KEY);
        assertThat(view.bucket()).isEqualTo(BUCKET);
        assertThat(view.sizeBytes()).isEqualTo(12_582_912L);
        assertThat(view.contentType()).isEqualTo("video/mp4");
        // 分块上传时服务端从没同时握有整个文件，算不出 sha256，这里如实留空。
        assertThat(view.sha256()).isNull();

        ArgumentCaptor<CompleteMultipartUploadRequest> request =
                ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
        verify(client).completeMultipartUpload(request.capture());
        assertThat(request.getValue().getPartETags())
                .extracting("partNumber", "eTag")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "etag-1"),
                        org.assertj.core.groups.Tuple.tuple(2, "etag-2"));
    }

    @Test
    void completeSortsPartsByNumberBecauseCosRequiresAscendingOrder() {
        when(client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(new CompleteMultipartUploadResult());
        ObjectMetadata stored = new ObjectMetadata();
        stored.setContentLength(3L);
        stored.setContentType("video/mp4");
        when(client.getObjectMetadata(BUCKET, KEY)).thenReturn(stored);

        service.completeMultipartUpload(
                UPLOAD_ID, KEY,
                List.of(new PartRef(3, "etag-3"), new PartRef(1, "etag-1"), new PartRef(2, "etag-2")));

        ArgumentCaptor<CompleteMultipartUploadRequest> request =
                ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
        verify(client).completeMultipartUpload(request.capture());
        assertThat(request.getValue().getPartETags())
                .extracting("partNumber")
                .containsExactly(1, 2, 3);
    }

    @Test
    void completeRejectsEmptyOrMalformedPartLists() {
        assertCode(() -> service.completeMultipartUpload(UPLOAD_ID, KEY, List.of()),
                "OBJECT_STORAGE_PARTS_INVALID");
        assertCode(() -> service.completeMultipartUpload(UPLOAD_ID, KEY, null),
                "OBJECT_STORAGE_PARTS_INVALID");
        assertCode(
                () -> service.completeMultipartUpload(
                        UPLOAD_ID, KEY, List.of(new PartRef(1, "etag-1"), new PartRef(1, "etag-2"))),
                "OBJECT_STORAGE_PARTS_INVALID");
        assertCode(
                () -> service.completeMultipartUpload(
                        UPLOAD_ID, KEY, List.of(new PartRef(0, "etag-1"))),
                "OBJECT_STORAGE_PART_NUMBER_INVALID");
        assertCode(
                () -> service.completeMultipartUpload(
                        UPLOAD_ID, KEY, List.of(new PartRef(1, "  "))),
                "OBJECT_STORAGE_PARTS_INVALID");
        verifyNoInteractions(client);
    }

    @Test
    void abortDelegatesToCos() {
        service.abortMultipartUpload(UPLOAD_ID, KEY);

        ArgumentCaptor<AbortMultipartUploadRequest> request =
                ArgumentCaptor.forClass(AbortMultipartUploadRequest.class);
        verify(client).abortMultipartUpload(request.capture());
        assertThat(request.getValue().getBucketName()).isEqualTo(BUCKET);
        assertThat(request.getValue().getKey()).isEqualTo(KEY);
        assertThat(request.getValue().getUploadId()).isEqualTo(UPLOAD_ID);
    }

    @Test
    void everyMultipartCallValidatesTheObjectKeyAndUploadId() {
        assertCode(() -> service.initiateMultipartUpload("../escape", "video/mp4"),
                "OBJECT_STORAGE_KEY_INVALID");
        assertCode(() -> service.uploadPart(UPLOAD_ID, "a b", 1, new byte[1024]),
                "OBJECT_STORAGE_KEY_INVALID");
        assertCode(() -> service.completeMultipartUpload(
                        UPLOAD_ID, "", List.of(new PartRef(1, "etag-1"))),
                "OBJECT_STORAGE_KEY_INVALID");
        assertCode(() -> service.abortMultipartUpload(UPLOAD_ID, "/leading-slash"),
                "OBJECT_STORAGE_KEY_INVALID");

        assertCode(() -> service.uploadPart("  ", KEY, 1, new byte[1024]),
                "OBJECT_STORAGE_UPLOAD_ID_INVALID");
        assertCode(() -> service.completeMultipartUpload(
                        null, KEY, List.of(new PartRef(1, "etag-1"))),
                "OBJECT_STORAGE_UPLOAD_ID_INVALID");
        assertCode(() -> service.abortMultipartUpload(null, KEY),
                "OBJECT_STORAGE_UPLOAD_ID_INVALID");
    }

    @Test
    void initiateRejectsAMissingOrOverlongContentType() {
        assertCode(() -> service.initiateMultipartUpload(KEY, " "),
                "OBJECT_STORAGE_CONTENT_TYPE_INVALID");
        assertCode(() -> service.initiateMultipartUpload(KEY, "v".repeat(256)),
                "OBJECT_STORAGE_CONTENT_TYPE_INVALID");
    }

    @Test
    void failsClosedWhenStorageIsNotConfigured() {
        when(clientProvider.getIfAvailable()).thenReturn(null);

        assertCode(() -> service.initiateMultipartUpload(KEY, "video/mp4"),
                "OBJECT_STORAGE_NOT_CONFIGURED");
        assertCode(() -> service.uploadPart(UPLOAD_ID, KEY, 1, new byte[1024]),
                "OBJECT_STORAGE_NOT_CONFIGURED");
        assertCode(() -> service.completeMultipartUpload(
                        UPLOAD_ID, KEY, List.of(new PartRef(1, "etag-1"))),
                "OBJECT_STORAGE_NOT_CONFIGURED");
        assertCode(() -> service.abortMultipartUpload(UPLOAD_ID, KEY),
                "OBJECT_STORAGE_NOT_CONFIGURED");
    }

    @Test
    void translatesCosFailuresIntoApiExceptions() {
        when(client.initiateMultipartUpload(any(InitiateMultipartUploadRequest.class)))
                .thenThrow(new com.qcloud.cos.exception.CosClientException("boom"));
        assertCode(() -> service.initiateMultipartUpload(KEY, "video/mp4"),
                "OBJECT_STORAGE_OPERATION_FAILED");

        when(client.uploadPart(any(UploadPartRequest.class)))
                .thenThrow(new com.qcloud.cos.exception.CosClientException("boom"));
        assertCode(() -> service.uploadPart(UPLOAD_ID, KEY, 1, new byte[1024]),
                "OBJECT_STORAGE_OPERATION_FAILED");

        when(client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenThrow(new com.qcloud.cos.exception.CosClientException("boom"));
        assertCode(() -> service.completeMultipartUpload(
                        UPLOAD_ID, KEY, List.of(new PartRef(1, "etag-1"))),
                "OBJECT_STORAGE_OPERATION_FAILED");
    }

    /**
     * abort 是清理路径：COS 那边已经没有这个会话时，硬要抛异常只会让调用方
     * 拿着一个删不掉的上传记录反复重试。这里把失败咽掉，但不假装成功以外的事。
     */
    @Test
    void abortSwallowsCosFailuresBecauseItIsACleanupPath() {
        org.mockito.Mockito.doThrow(new com.qcloud.cos.exception.CosClientException("gone"))
                .when(client).abortMultipartUpload(any(AbortMultipartUploadRequest.class));

        service.abortMultipartUpload(UPLOAD_ID, KEY);

        verify(client).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
    }

    private static void assertCode(Operation operation, String expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    @FunctionalInterface
    private interface Operation {
        void run();
    }
}
