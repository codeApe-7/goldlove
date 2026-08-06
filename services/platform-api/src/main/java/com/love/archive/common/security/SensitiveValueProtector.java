package com.love.archive.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class SensitiveValueProtector {

    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_256_KEY_LENGTH = 32;
    private static final int MINIMUM_HMAC_KEY_LENGTH = 32;
    private static final int GCM_TAG_LENGTH = 16;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec hmacKey;
    private final SecureRandom secureRandom;

    public SensitiveValueProtector(
            String encryptionKeyBase64,
            String hmacKeyBase64,
            SecureRandom secureRandom) {
        byte[] decodedEncryptionKey = decodeKey(encryptionKeyBase64, "敏感数据加密密钥不是合法的 Base64");
        try {
            if (decodedEncryptionKey.length != AES_256_KEY_LENGTH) {
                throw new IllegalArgumentException("敏感数据加密密钥必须为 32 字节");
            }
            byte[] decodedHmacKey = decodeKey(hmacKeyBase64, "敏感数据 HMAC 密钥不是合法的 Base64");
            try {
                if (decodedHmacKey.length < MINIMUM_HMAC_KEY_LENGTH) {
                    throw new IllegalArgumentException("敏感数据 HMAC 密钥至少为 32 字节");
                }

                this.encryptionKey = new SecretKeySpec(decodedEncryptionKey, "AES");
                this.hmacKey = new SecretKeySpec(decodedHmacKey, "HmacSHA256");
                this.secureRandom = java.util.Objects.requireNonNull(secureRandom, "secureRandom");
            } finally {
                Arrays.fill(decodedHmacKey, (byte) 0);
            }
        } finally {
            Arrays.fill(decodedEncryptionKey, (byte) 0);
        }
    }

    public byte[] encrypt(String domain, String plaintext) {
        byte[] domainBytes = domainBytes(domain);
        byte[] plaintextBytes = valueBytes(plaintext, "plaintext");
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(domainBytes);
            byte[] encrypted = cipher.doFinal(plaintextBytes);
            return ByteBuffer.allocate(1 + NONCE_LENGTH + encrypted.length)
                    .put(FORMAT_VERSION)
                    .put(nonce)
                    .put(encrypted)
                    .array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("敏感数据加密失败", exception);
        } finally {
            Arrays.fill(domainBytes, (byte) 0);
            Arrays.fill(plaintextBytes, (byte) 0);
        }
    }

    public String decrypt(String domain, byte[] ciphertext) {
        byte[] domainBytes = domainBytes(domain);
        if (ciphertext == null || ciphertext.length < 1 + NONCE_LENGTH + GCM_TAG_LENGTH) {
            Arrays.fill(domainBytes, (byte) 0);
            throw new IllegalArgumentException("敏感数据密文格式不正确");
        }
        ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
        if (buffer.get() != FORMAT_VERSION) {
            Arrays.fill(domainBytes, (byte) 0);
            throw new IllegalArgumentException("不支持的敏感数据密文版本");
        }
        byte[] nonce = new byte[NONCE_LENGTH];
        buffer.get(nonce);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(domainBytes);
            byte[] plaintext = cipher.doFinal(encrypted);
            try {
                return new String(plaintext, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("敏感数据密文校验失败", exception);
        } finally {
            Arrays.fill(domainBytes, (byte) 0);
        }
    }

    public String hmac(String domain, String normalizedValue) {
        requireDomain(domain);
        requireValue(normalizedValue, "normalizedValue");
        byte[] input = (domain + ':' + normalizedValue).getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(input));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("敏感数据检索摘要计算失败", exception);
        } finally {
            Arrays.fill(input, (byte) 0);
        }
    }

    private static byte[] domainBytes(String domain) {
        requireDomain(domain);
        return domain.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] valueBytes(String value, String name) {
        requireValue(value, name);
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void requireDomain(String domain) {
        requireValue(domain, "domain");
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static byte[] decodeKey(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(errorMessage, exception);
        }
    }
}
