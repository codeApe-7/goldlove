package com.love.archive.identity.security;

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

public final class PhoneProtector {

    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_256_KEY_LENGTH = 32;
    private static final int MINIMUM_HMAC_KEY_LENGTH = 32;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec searchKey;
    private final SecureRandom secureRandom;

    public PhoneProtector(String encryptionKeyBase64, String searchKeyBase64, SecureRandom secureRandom) {
        byte[] decodedEncryptionKey = decodeKey(encryptionKeyBase64, "手机号加密密钥不是合法的 Base64");
        if (decodedEncryptionKey.length != AES_256_KEY_LENGTH) {
            throw new IllegalArgumentException("手机号加密密钥必须为 32 字节");
        }
        byte[] decodedSearchKey = decodeKey(searchKeyBase64, "手机号检索密钥不是合法的 Base64");
        if (decodedSearchKey.length < MINIMUM_HMAC_KEY_LENGTH) {
            throw new IllegalArgumentException("手机号检索密钥至少为 32 字节");
        }

        this.encryptionKey = new SecretKeySpec(decodedEncryptionKey, "AES");
        this.searchKey = new SecretKeySpec(decodedSearchKey, "HmacSHA256");
        this.secureRandom = java.util.Objects.requireNonNull(secureRandom, "secureRandom");
        Arrays.fill(decodedEncryptionKey, (byte) 0);
        Arrays.fill(decodedSearchKey, (byte) 0);
    }

    public byte[] encrypt(String normalizedPhone) {
        java.util.Objects.requireNonNull(normalizedPhone, "normalizedPhone");
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        byte[] plaintext = normalizedPhone.getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(1 + NONCE_LENGTH + encrypted.length)
                    .put(FORMAT_VERSION)
                    .put(nonce)
                    .put(encrypted)
                    .array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("手机号加密失败", exception);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    public String decrypt(byte[] protectedPhone) {
        if (protectedPhone == null || protectedPhone.length < 1 + NONCE_LENGTH + 16) {
            throw new IllegalArgumentException("手机号密文格式不正确");
        }
        ByteBuffer buffer = ByteBuffer.wrap(protectedPhone);
        if (buffer.get() != FORMAT_VERSION) {
            throw new IllegalArgumentException("不支持的手机号密文版本");
        }
        byte[] nonce = new byte[NONCE_LENGTH];
        buffer.get(nonce);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] plaintext = cipher.doFinal(encrypted);
            try {
                return new String(plaintext, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("手机号密文校验失败", exception);
        }
    }

    public String searchHash(String normalizedPhone) {
        java.util.Objects.requireNonNull(normalizedPhone, "normalizedPhone");
        byte[] input = normalizedPhone.getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(searchKey);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(input));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("手机号检索摘要计算失败", exception);
        } finally {
            Arrays.fill(input, (byte) 0);
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
