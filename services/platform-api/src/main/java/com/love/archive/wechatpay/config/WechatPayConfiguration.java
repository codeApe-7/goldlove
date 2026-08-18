package com.love.archive.wechatpay.config;

import com.love.archive.wechatpay.support.JdkWechatHttpClient;
import com.love.archive.wechatpay.support.WechatHttpClient;
import com.love.archive.wechatpay.support.WechatPayCryptography;
import java.security.SecureRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class WechatPayConfiguration {

    /** 凭据齐全才装配签名器；缺失时渠道方法统一返回 PAYMENT_CHANNEL_NOT_CONFIGURED。 */
    @Bean
    @Conditional(WechatPayCredentialsConfigured.class)
    WechatPayCryptography wechatPayCryptography(WechatPayProperties properties) {
        return new WechatPayCryptography(
                properties.merchantId(),
                properties.merchantSerialNumber(),
                properties.merchantPrivateKey(),
                properties.platformPublicKeyId(),
                properties.platformPublicKey(),
                properties.apiV3Key(),
                new SecureRandom());
    }

    @Bean
    @ConditionalOnMissingBean(WechatHttpClient.class)
    WechatHttpClient wechatHttpClient() {
        return new JdkWechatHttpClient();
    }

    static final class WechatPayCredentialsConfigured implements Condition {

        private static final String[] REQUIRED_PROPERTIES = {
                "app.wechat.pay.app-id",
                "app.wechat.pay.app-secret",
                "app.wechat.pay.merchant-id",
                "app.wechat.pay.merchant-serial-number",
                "app.wechat.pay.merchant-private-key",
                "app.wechat.pay.api-v3-key",
                "app.wechat.pay.platform-public-key-id",
                "app.wechat.pay.platform-public-key",
                "app.wechat.pay.notify-url",
        };

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var environment = context.getEnvironment();
            for (String property : REQUIRED_PROPERTIES) {
                if (!StringUtils.hasText(environment.getProperty(property))) {
                    return false;
                }
            }
            return true;
        }
    }
}
