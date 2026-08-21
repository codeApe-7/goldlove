package com.love.archive.xpay.config;

import com.love.archive.xpay.support.JdkXpayHttpClient;
import com.love.archive.xpay.support.XpayHttpClient;
import com.love.archive.xpay.support.XpayCryptography;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class XpayConfiguration {

    /** 凭据齐全才装配签名器；缺失时渠道方法统一返回 PAYMENT_CHANNEL_NOT_CONFIGURED。 */
    @Bean
    @Conditional(XpayCredentialsConfigured.class)
    XpayCryptography xpayCryptography(XpayProperties properties) {
        return new XpayCryptography(
                properties.merchantPrivateKey(), properties.platformPublicKey());
    }

    @Bean
    @ConditionalOnMissingBean(XpayHttpClient.class)
    XpayHttpClient xpayHttpClient() {
        return new JdkXpayHttpClient();
    }

    static final class XpayCredentialsConfigured implements Condition {

        private static final String[] REQUIRED_PROPERTIES = {
                "app.xpay.pid",
                "app.xpay.merchant-private-key",
                "app.xpay.platform-public-key",
                "app.xpay.notify-url",
                "app.xpay.base-url",
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
