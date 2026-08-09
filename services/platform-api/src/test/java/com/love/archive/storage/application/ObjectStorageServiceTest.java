package com.love.archive.storage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.love.archive.common.web.ApiException;
import com.love.archive.storage.config.CosStorageProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class ObjectStorageServiceTest {

    private static final String BUCKET = "loveplatform-1314980040";
    private static final String KEY = "profiles/1/photo.jpg";

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
    void putStoresBytesWithSha256MetadataAndReturnsTypedView() {
        byte[] content = "hello-cos".getBytes(StandardCharsets.UTF_8);

        StoredObjectView stored = service.put(KEY, content, "image/jpeg");

        assertThat(stored.objectKey()).isEqualTo(KEY);
        assertThat(stored.bucket()).isEqualTo(BUCKET);
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(stored.sha256()).isEqualTo(sha256(content));
        ArgumentCaptor<ObjectMetadata> metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(client).putObject(
                eq(BUCKET),
                eq(KEY),
                any(ByteArrayInputStream.class),
                metadata.capture());
        assertThat(metadata.getValue().getContentLength()).isEqualTo(content.length);
        assertThat(metadata.getValue().getContentType()).isEqualTo("image/jpeg");
        assertThat(metadata.getValue().getUserMetadata().get("sha256"))
                .isEqualTo(sha256(content));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "../escape", "/leading-slash", "a b", "a?b", "a\\b"})
    void rejectsUnsafeObjectKeys(String unsafeKey) {
        assertCode(() -> service.put(unsafeKey, new byte[] {1}, "text/plain"),
                "OBJECT_STORAGE_KEY_INVALID");
        assertCode(() -> service.exists(unsafeKey), "OBJECT_STORAGE_KEY_INVALID");
        assertCode(() -> service.signDownloadUrl(unsafeKey, Duration.ofMinutes(5)),
                "OBJECT_STORAGE_KEY_INVALID");
        assertCode(() -> service.delete(unsafeKey), "OBJECT_STORAGE_KEY_INVALID");
    }

    @Test
    void rejectsEmptyAndOversizedContent() {
        assertCode(() -> service.put(KEY, new byte[0], "text/plain"),
                "OBJECT_STORAGE_CONTENT_INVALID");
        assertCode(() -> service.put(KEY, null, "text/plain"),
                "OBJECT_STORAGE_CONTENT_INVALID");

        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        assertCode(() -> service.put(KEY, oversized, "text/plain"),
                "OBJECT_STORAGE_OBJECT_TOO_LARGE");
    }

    @Test
    void signDownloadUrlDelegatesWithRequestedExpiry() throws Exception {
        when(client.generatePresignedUrl(eq(BUCKET), eq(KEY), any(Date.class)))
                .thenReturn(new URL("https://" + BUCKET + ".cos.ap-guangzhou.myqcloud.com/"
                        + KEY + "?signature=placeholder"));

        String url = service.signDownloadUrl(KEY, Duration.ofMinutes(5));

        assertThat(url).startsWith("https://" + BUCKET + ".cos.ap-guangzhou.myqcloud.com/");
        verify(client).generatePresignedUrl(eq(BUCKET), eq(KEY), any(Date.class));
    }

    @Test
    void rejectsInvalidDownloadUrlTtl() {
        assertCode(() -> service.signDownloadUrl(KEY, null), "OBJECT_STORAGE_URL_TTL_INVALID");
        assertCode(() -> service.signDownloadUrl(KEY, Duration.ZERO),
                "OBJECT_STORAGE_URL_TTL_INVALID");
        assertCode(() -> service.signDownloadUrl(KEY, Duration.ofMinutes(-1)),
                "OBJECT_STORAGE_URL_TTL_INVALID");
        assertCode(() -> service.signDownloadUrl(KEY, Duration.ofDays(8)),
                "OBJECT_STORAGE_URL_TTL_INVALID");
    }

    @Test
    void existsAndDeleteDelegateToClient() {
        when(client.doesObjectExist(BUCKET, KEY)).thenReturn(true);
        assertThat(service.exists(KEY)).isTrue();
        when(client.doesObjectExist(BUCKET, KEY)).thenReturn(false);
        assertThat(service.exists(KEY)).isFalse();

        service.delete(KEY);
        verify(client).deleteObject(BUCKET, KEY);
    }

    @Test
    void failsClosedWhenStorageIsNotConfigured() {
        when(clientProvider.getIfAvailable()).thenReturn(null);

        assertCode(() -> service.put(KEY, new byte[] {1}, "text/plain"),
                "OBJECT_STORAGE_NOT_CONFIGURED");
        assertCode(() -> service.exists(KEY), "OBJECT_STORAGE_NOT_CONFIGURED");
        assertCode(() -> service.signDownloadUrl(KEY, Duration.ofMinutes(5)),
                "OBJECT_STORAGE_NOT_CONFIGURED");
        assertCode(() -> service.delete(KEY), "OBJECT_STORAGE_NOT_CONFIGURED");
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
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
