package com.love.archive.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.storage.application.StoredObjectView;
import com.love.archive.storage.config.CosStorageProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * Live verification against the real Tencent Cloud COS bucket. Disabled by default;
 * run with -Dcos.live.smoke=true and COS_* environment variables exported.
 */
@EnabledIfSystemProperty(named = "cos.live.smoke", matches = "true")
class CosLiveSmokeTest {

    @Test
    void putsPresignsFetchesAndDeletesAgainstConfiguredBucket() throws Exception {
        String secretId = requireEnv("COS_SECRET_ID");
        String secretKey = requireEnv("COS_SECRET_KEY");
        String region = System.getenv().getOrDefault("COS_REGION", "ap-guangzhou");
        String bucket = requireEnv("COS_BUCKET");

        COSClient client = new COSClient(
                new BasicCOSCredentials(secretId, secretKey),
                new ClientConfig(new Region(region)));
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("cosClient", client);
        ObjectProvider<COSClient> clientProvider = factory.getBeanProvider(COSClient.class);
        ObjectStorageService service = new ObjectStorageService(
                clientProvider,
                new CosStorageProperties(secretId, secretKey, region, bucket));
        String objectKey = "smoke-tests/" + UUID.randomUUID() + ".txt";
        byte[] content = "tencent-cos-live-smoke".getBytes(StandardCharsets.UTF_8);
        try {
            StoredObjectView stored = service.put(objectKey, content, "text/plain");
            assertThat(stored.sha256()).hasSize(64);
            assertThat(stored.sizeBytes()).isEqualTo(content.length);
            assertThat(stored.bucket()).isEqualTo(bucket);
            assertThat(service.exists(objectKey)).isTrue();

            String url = service.signDownloadUrl(objectKey, Duration.ofMinutes(5));
            assertThat(url).contains(bucket);
            try (HttpClient http = HttpClient.newHttpClient()) {
                HttpResponse<byte[]> response = http.send(
                        HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).isEqualTo(content);
            }
        } finally {
            service.delete(objectKey);
            assertThat(service.exists(objectKey)).isFalse();
        }
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }
}
