package com.love.archive.storage.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosStorageConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(CosStorageConfiguration.class);

    @Test
    void createsClientOnlyWhenEveryCosCredentialIsConfigured() {
        contextRunner
                .withPropertyValues(
                        "app.storage.cos.secret-id=",
                        "app.storage.cos.secret-key=",
                        "app.storage.cos.region=",
                        "app.storage.cos.bucket=")
                .run(context -> assertThat(context).doesNotHaveBean(COSClient.class));

        contextRunner
                .withPropertyValues(
                        "app.storage.cos.secret-id=secret-id",
                        "app.storage.cos.secret-key=secret-key",
                        "app.storage.cos.region=ap-guangzhou",
                        "app.storage.cos.bucket=loveplatform-1314980040")
                .run(context -> assertThat(context).hasSingleBean(COSClient.class));

        contextRunner
                .withPropertyValues(
                        "app.storage.cos.secret-id=secret-id",
                        "app.storage.cos.secret-key=secret-key",
                        "app.storage.cos.region=ap-guangzhou")
                .run(context -> assertThat(context).doesNotHaveBean(COSClient.class));

        contextRunner
                .withPropertyValues(
                        "app.storage.cos.secret-id=secret-id",
                        "app.storage.cos.secret-key=secret-key",
                        "app.storage.cos.region=ap-guangzhou",
                        "app.storage.cos.bucket=loveplatform-1314980040")
                .run(context -> {
                    assertThat(context).hasSingleBean(COSClient.class);
                    ClientConfig config = context.getBean(COSClient.class).getClientConfig();
                    assertThat(config.getConnectionTimeout()).isEqualTo(5_000);
                    assertThat(config.getSocketTimeout()).isEqualTo(20_000);
                    assertThat(config.getRequestTimeout()).isEqualTo(30_000);
                    assertThat(config.getRequestTimeOutEnable()).isTrue();
                    assertThat(config.getMaxErrorRetry()).isEqualTo(3);
                });
    }
}
