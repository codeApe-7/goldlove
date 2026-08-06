package com.love.archive.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PhoneProtectorTest {

    private static final String ENCRYPTION_KEY = base64Key(32, (byte) 0x11);
    private static final String SEARCH_KEY = base64Key(32, (byte) 0x22);

    private final PhoneProtector protector = new PhoneProtector(
            ENCRYPTION_KEY,
            SEARCH_KEY,
            new SecureRandom());

    @Test
    void encryptsAndDecryptsPhone() {
        byte[] ciphertext = protector.encrypt("13800138000");

        assertThat(ciphertext).isNotEqualTo("13800138000".getBytes());
        assertThat(protector.decrypt(ciphertext)).isEqualTo("13800138000");
    }

    @Test
    void usesAUniqueNonceForEveryEncryption() {
        assertThat(protector.encrypt("13800138000"))
                .isNotEqualTo(protector.encrypt("13800138000"));
    }

    @Test
    void createsDeterministicKeyedSearchHash() {
        assertThat(protector.searchHash("13800138000"))
                .isEqualTo(protector.searchHash("13800138000"))
                .isNotEqualTo(protector.searchHash("13900139000"));
    }

    @Test
    void rejectsAnInvalidEncryptionKeyLength() {
        String shortKey = base64Key(16, (byte) 0x33);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PhoneProtector(shortKey, SEARCH_KEY, new SecureRandom()))
                .withMessage("手机号加密密钥必须为 32 字节");
    }

    private static String base64Key(int length, byte value) {
        byte[] key = new byte[length];
        java.util.Arrays.fill(key, value);
        return Base64.getEncoder().encodeToString(key);
    }
}
