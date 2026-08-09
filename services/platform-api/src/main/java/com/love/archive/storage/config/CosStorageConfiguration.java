package com.love.archive.storage.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CosStorageProperties.class)
public class CosStorageConfiguration {

    @Bean
    @Conditional(CosCredentialsConfigured.class)
    COSClient cosClient(CosStorageProperties properties) {
        BasicCOSCredentials credentials = new BasicCOSCredentials(
                properties.secretId(), properties.secretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(properties.region()));
        return new COSClient(credentials, clientConfig);
    }

    static final class CosCredentialsConfigured implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var environment = context.getEnvironment();
            return StringUtils.hasText(environment.getProperty("app.storage.cos.secret-id"))
                    && StringUtils.hasText(environment.getProperty("app.storage.cos.secret-key"))
                    && StringUtils.hasText(environment.getProperty("app.storage.cos.region"))
                    && StringUtils.hasText(environment.getProperty("app.storage.cos.bucket"));
        }
    }
}
