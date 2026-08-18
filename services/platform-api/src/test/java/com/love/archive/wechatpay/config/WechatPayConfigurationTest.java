package com.love.archive.wechatpay.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.love.archive.wechatpay.support.WechatHttpClient;
import com.love.archive.wechatpay.support.WechatPayCryptography;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class WechatPayConfigurationTest {

    private static KeyPair merchantKeyPair;
    private static KeyPair platformKeyPair;

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

    @BeforeAll
    static void generateKeys() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        merchantKeyPair = generator.generateKeyPair();
        platformKeyPair = generator.generateKeyPair();
    }

    @Test
    void doesNotAssembleCryptographyWithoutCredentialsButStillStartsUp() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(WechatPayCryptography.class);
            assertThat(context).hasSingleBean(WechatHttpClient.class);
        });
    }

    @Test
    void assemblesCryptographyOnlyWhenEveryCredentialIsPresent() {
        contextRunner.withPropertyValues(credentials()).run(context -> {
            assertThat(context).hasSingleBean(WechatPayCryptography.class);
            assertThat(context.getBean(WechatPayCryptography.class).platformPublicKeyId())
                    .isEqualTo("PUB_KEY_ID_TEST");
        });

        // 少任何一项都不装配。
        for (String omitted : new String[] {
                "app.wechat.pay.app-id",
                "app.wechat.pay.app-secret",
                "app.wechat.pay.merchant-id",
                "app.wechat.pay.merchant-serial-number",
                "app.wechat.pay.merchant-private-key",
                "app.wechat.pay.api-v3-key",
                "app.wechat.pay.platform-public-key-id",
                "app.wechat.pay.platform-public-key",
                "app.wechat.pay.notify-url",
        }) {
            String[] values = java.util.Arrays.stream(credentials())
                    .filter(entry -> !entry.startsWith(omitted + "="))
                    .toArray(String[]::new);
            contextRunner.withPropertyValues(values).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context)
                        .withFailMessage("缺少 %s 时不应装配渠道签名器", omitted)
                        .doesNotHaveBean(WechatPayCryptography.class);
            });
        }
    }

    @Test
    void resolvesGatewayDefaultsWhenBaseUrlsAreBlank() {
        contextRunner.withPropertyValues(credentials()).run(context -> {
            WechatPayProperties properties = context.getBean(WechatPayProperties.class);
            assertThat(properties.resolvedApiBaseUrl()).isEqualTo("https://api.mch.weixin.qq.com");
            assertThat(properties.resolvedOauthBaseUrl()).isEqualTo("https://api.weixin.qq.com");
            assertThat(properties.complete()).isTrue();
        });

        contextRunner
                .withPropertyValues(credentials())
                .withPropertyValues("app.wechat.pay.api-base-url=https://pay.example.test/")
                .run(context -> assertThat(context.getBean(WechatPayProperties.class).resolvedApiBaseUrl())
                        .isEqualTo("https://pay.example.test"));
    }

    private static String[] credentials() {
        return new String[] {
                "app.wechat.pay.app-id=wx-app-1",
                "app.wechat.pay.app-secret=wx-secret-1",
                "app.wechat.pay.merchant-id=1900000109",
                "app.wechat.pay.merchant-serial-number=SERIAL-1",
                "app.wechat.pay.merchant-private-key="
                        + pem("PRIVATE KEY", merchantKeyPair.getPrivate().getEncoded()),
                "app.wechat.pay.api-v3-key=0123456789abcdef0123456789abcdef",
                "app.wechat.pay.platform-public-key-id=PUB_KEY_ID_TEST",
                "app.wechat.pay.platform-public-key="
                        + pem("PUBLIC KEY", platformKeyPair.getPublic().getEncoded()),
                "app.wechat.pay.notify-url=https://api.example.test/notify",
        };
    }

    /** 属性值写成单行，换行用空格代替；PEM 解析会忽略空白。 */
    private static String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "----- "
                + Base64.getMimeEncoder(64, " ".getBytes(StandardCharsets.UTF_8)).encodeToString(encoded)
                + " -----END " + label + "-----";
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WechatPayProperties.class)
    @org.springframework.context.annotation.Import(WechatPayConfiguration.class)
    static class TestConfiguration {
    }
}
