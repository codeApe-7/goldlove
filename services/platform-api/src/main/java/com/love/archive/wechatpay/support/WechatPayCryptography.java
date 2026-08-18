package com.love.archive.wechatpay.support;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 微信支付 API v3 的签名、验签与回调资源解密。
 * 商户私钥、平台公钥与 API v3 密钥只在本对象内驻留内存，不写日志、不出模块。
 */
public final class WechatPayCryptography {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_CHARACTERS = 32;
    private static final String NONCE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final String merchantId;
    private final String merchantSerialNumber;
    private final String platformPublicKeyId;
    private final PrivateKey merchantPrivateKey;
    private final PublicKey platformPublicKey;
    private final SecretKeySpec apiV3Key;
    private final SecureRandom secureRandom;

    public WechatPayCryptography(
            String merchantId,
            String merchantSerialNumber,
            String merchantPrivateKeyPem,
            String platformPublicKeyId,
            String platformPublicKeyPem,
            String apiV3Key,
            SecureRandom secureRandom) {
        this.merchantId = requireText(merchantId, "商户号");
        this.merchantSerialNumber = requireText(merchantSerialNumber, "商户证书序列号");
        this.platformPublicKeyId = requireText(platformPublicKeyId, "平台公钥 ID");
        this.merchantPrivateKey = parsePrivateKey(requireText(merchantPrivateKeyPem, "商户私钥"));
        this.platformPublicKey = parsePublicKey(requireText(platformPublicKeyPem, "平台公钥"));
        byte[] keyBytes = requireText(apiV3Key, "API v3 密钥").getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("API v3 密钥必须为 32 字节");
        }
        this.apiV3Key = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public String platformPublicKeyId() {
        return platformPublicKeyId;
    }

    public String nonce() {
        StringBuilder builder = new StringBuilder(NONCE_CHARACTERS);
        for (int index = 0; index < NONCE_CHARACTERS; index++) {
            builder.append(NONCE_ALPHABET.charAt(secureRandom.nextInt(NONCE_ALPHABET.length())));
        }
        return builder.toString();
    }

    /**
     * 构造 API v3 请求签名头。签名串为 方法\n路径\n时间戳\n随机串\n请求体\n。
     */
    public String authorizationHeader(
            String httpMethod,
            String canonicalUrl,
            String body,
            String timestamp,
            String nonce) {
        String message = httpMethod + "\n" + canonicalUrl + "\n" + timestamp + "\n" + nonce + "\n"
                + (body == null ? "" : body) + "\n";
        String signature = sign(message);
        return "WECHATPAY2-SHA256-RSA2048 "
                + "mchid=\"" + merchantId + "\","
                + "nonce_str=\"" + nonce + "\","
                + "signature=\"" + signature + "\","
                + "timestamp=\"" + timestamp + "\","
                + "serial_no=\"" + merchantSerialNumber + "\"";
    }

    /** 前端调起支付所需的 paySign，签名串为 appId\n时间戳\n随机串\nprepay_id\n。 */
    public String signJsapiPayment(String appId, String timestamp, String nonce, String prepayId) {
        return sign(appId + "\n" + timestamp + "\n" + nonce + "\n" + prepayId + "\n");
    }

    /** 回调验签，签名串为 时间戳\n随机串\n请求体\n。 */
    public boolean verifyNotifySignature(String timestamp, String nonce, String body, String signatureBase64) {
        if (signatureBase64 == null || signatureBase64.isBlank()) {
            return false;
        }
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signatureBase64);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        String message = timestamp + "\n" + nonce + "\n" + (body == null ? "" : body) + "\n";
        try {
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(platformPublicKey);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signatureBytes);
        } catch (GeneralSecurityException exception) {
            return false;
        }
    }

    /** 解密回调资源（AEAD_AES_256_GCM）。 */
    public String decryptResource(String associatedData, String nonce, String ciphertextBase64) {
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(ciphertextBase64, "ciphertext");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    apiV3Key,
                    new GCMParameterSpec(GCM_TAG_BITS, nonce.getBytes(StandardCharsets.UTF_8)));
            if (associatedData != null && !associatedData.isEmpty()) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertextBase64));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("回调资源解密失败", exception);
        }
    }

    private String sign(String message) {
        try {
            Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM);
            signer.initSign(merchantPrivateKey);
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("支付请求签名失败", exception);
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
        String normalized = pem.strip();
        if (normalized.contains("BEGIN CERTIFICATE")) {
            byte[] decoded = decodePem(normalized, "CERTIFICATE", "平台证书");
            try {
                X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(decoded));
                return certificate.getPublicKey();
            } catch (GeneralSecurityException exception) {
                throw new IllegalArgumentException("平台证书格式不正确", exception);
            }
        }
        byte[] decoded = decodePem(normalized, "PUBLIC KEY", "平台公钥");
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
