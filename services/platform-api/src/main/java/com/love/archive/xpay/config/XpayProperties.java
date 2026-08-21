package com.love.archive.xpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 易支付（XPay V2）渠道配置。所有密钥只从服务端环境变量注入，绝不下发前端。
 *
 * @param pid                商户 ID
 * @param merchantPrivateKey 商户私钥（PKCS#8 PEM），用于请求签名
 * @param platformPublicKey  平台公钥（X.509 PEM），用于验签响应与通知
 * @param notifyUrl          异步回调地址，指向 xpay 通知端点
 * @param returnUrl          支付完成后同步跳转地址
 * @param baseUrl            API 网关地址，必填。刻意不给默认值——
 *                           渠道服务商的域名属于部署配置，不该硬编码进源码
 */
@ConfigurationProperties(prefix = "app.xpay")
public record XpayProperties(
        String pid,
        String merchantPrivateKey,
        String platformPublicKey,
        String notifyUrl,
        String returnUrl,
        String baseUrl) {

    public String resolvedBaseUrl() {
        return stripTrailingSlash(baseUrl);
    }

    /** 缺任何一项凭据都不装配渠道客户端，业务侧返回 PAYMENT_CHANNEL_NOT_CONFIGURED。 */
    public boolean complete() {
        return hasText(pid)
                && hasText(merchantPrivateKey)
                && hasText(platformPublicKey)
                && hasText(notifyUrl)
                && hasText(baseUrl);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
