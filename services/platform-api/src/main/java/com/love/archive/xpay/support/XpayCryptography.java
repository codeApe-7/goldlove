package com.love.archive.xpay.support;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 易支付（XPay V2）RSA2 双向签名。
 * 商户私钥对请求签名，平台公钥对响应与通知验签；签名串为参数按字段名字典序升序拼接
 * （排除 sign、sign_type 与空值）得到的 {@code k1=v1&k2=v2}。
 */
public final class XpayCryptography {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private final PrivateKey merchantPrivateKey;
    private final PublicKey platformPublicKey;

    public XpayCryptography(String merchantPrivateKeyPem, String platformPublicKeyPem) {
        this.merchantPrivateKey = parsePrivateKey(requireText(merchantPrivateKeyPem, "商户私钥"));
        this.platformPublicKey = parsePublicKey(requireText(platformPublicKeyPem, "平台公钥"));
    }

    /** 对参数集签名，返回 Base64 编码的签名。 */
    public String sign(Map<String, String> params) {
        return sign(canonicalize(params));
    }

    /** 用平台公钥验证参数集与签名。 */
    public boolean verify(Map<String, String> params, String signatureBase64) {
        if (signatureBase64 == null || signatureBase64.isBlank()) {
            return false;
        }
        try {
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(platformPublicKey);
            verifier.update(canonicalize(params).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signatureBytes);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return false;
        }
    }

    /** 按字段名字典序升序拼接，排除 sign / sign_type 及空值。 */
    public static String canonicalize(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> !"sign".equals(entry.getKey()) && !"sign_type".equals(entry.getKey()))
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private String sign(String message) {
        try {
            Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM);
            signer.initSign(merchantPrivateKey);
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("易支付请求签名失败", exception);
        }
    }

    private static PrivateKey parsePrivateKey(String pem) {
        byte[] decoded = decodePem(pem, "PRIVATE KEY", "商户私钥");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("商户私钥格式不正确，需要 PKCS#8 PEM", exception);
        }
    }

    private static PublicKey parsePublicKey(String pem) {
        byte[] decoded = decodePem(pem, "PUBLIC KEY", "平台公钥");
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("平台公钥格式不正确，需要 X.509 PEM", exception);
        }
    }

    private static byte[] decodePem(String pem, String label, String description) {
        String body = pem.strip()
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        if (body.isEmpty()) {
            throw new IllegalArgumentException(description + "为空");
        }
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(description + "不是合法的 Base64 PEM", exception);
        }
    }

    private static String requireText(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(description + "未配置");
        }
        return value.strip();
    }
}
