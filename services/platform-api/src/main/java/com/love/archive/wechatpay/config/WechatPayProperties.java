package com.love.archive.wechatpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信支付渠道配置。所有密钥只从服务端环境变量注入，绝不下发前端。
 *
 * @param appId                公众号 AppID
 * @param appSecret            公众号 AppSecret（服务端专用）
 * @param merchantId           商户号
 * @param merchantSerialNumber 商户 API 证书序列号
 * @param merchantPrivateKey   商户 API 私钥（PKCS#8 PEM）
 * @param apiV3Key             API v3 密钥，用于回调资源解密
 * @param platformPublicKeyId  微信支付平台公钥 ID
 * @param platformPublicKey    微信支付平台公钥（X.509 PEM），用于回调验签
 * @param notifyUrl            回调地址，必须是公网 HTTPS
 * @param oauthRedirectUri     公众号网页授权回跳地址，服务端固定，避免开放重定向
 * @param apiBaseUrl           微信支付 API 网关
 * @param oauthBaseUrl         公众号网页授权网关
 */
@ConfigurationProperties(prefix = "app.wechat.pay")
public record WechatPayProperties(
        String appId,
        String appSecret,
        String merchantId,
        String merchantSerialNumber,
        String merchantPrivateKey,
        String apiV3Key,
        String platformPublicKeyId,
        String platformPublicKey,
        String notifyUrl,
        String oauthRedirectUri,
        String apiBaseUrl,
        String oauthBaseUrl) {

    private static final String DEFAULT_API_BASE_URL = "https://api.mch.weixin.qq.com";
    private static final String DEFAULT_OAUTH_BASE_URL = "https://api.weixin.qq.com";

    public String resolvedApiBaseUrl() {
        return hasText(apiBaseUrl) ? stripTrailingSlash(apiBaseUrl) : DEFAULT_API_BASE_URL;
    }

    public String resolvedOauthBaseUrl() {
        return hasText(oauthBaseUrl) ? stripTrailingSlash(oauthBaseUrl) : DEFAULT_OAUTH_BASE_URL;
    }

    /** 缺任何一项凭据都不装配渠道客户端，业务侧返回 PAYMENT_CHANNEL_NOT_CONFIGURED。 */
    public boolean complete() {
        return hasText(appId)
                && hasText(appSecret)
                && hasText(merchantId)
                && hasText(merchantSerialNumber)
                && hasText(merchantPrivateKey)
                && hasText(apiV3Key)
                && hasText(platformPublicKeyId)
                && hasText(platformPublicKey)
                && hasText(notifyUrl);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String stripTrailingSlash(String value) {
        String trimmed = value.strip();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
