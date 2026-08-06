package com.love.archive.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SensitiveValueProtectorTest {

    private static final String ENCRYPTION_KEY = base64Key(32, (byte) 0x41);
    private static final String HMAC_KEY = base64Key(32, (byte) 0x42);

    private final SensitiveValueProtector protector = new SensitiveValueProtector(
            ENCRYPTION_KEY,
            HMAC_KEY,
            new SecureRandom());

    @Test
    void separatesDomainsAndRandomizesCiphertext() {
        byte[] first = protector.encrypt("profile:wechat-id", "wx-alice");
        byte[] second = protector.encrypt("profile:wechat-id", "wx-alice");

        assertThat(first).isNotEqualTo(second);
        assertThat(protector.decrypt("profile:wechat-id", first)).isEqualTo("wx-alice");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> protector.decrypt("profile:douyin-id", first));
        assertThat(protector.hmac("consent:ip", "203.0.113.8"))
                .isNotEqualTo(protector.hmac("profile:wechat-id", "203.0.113.8"));
    }

    @Test
    void hmacUsesColonSeparatedInputAndUnpaddedUrlSafeBase64() {
        assertThat(protector.hmac("consent:ip", "203.0.113.8"))
                .isEqualTo("6QcreNtGgIyLJMEwf93D_qH2FcYAy0YkvmXYNFRGQk4");
    }

    @Test
    void rejectsInvalidKeysDomainsValuesAndCiphertext() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SensitiveValueProtector(
                        "not-base64", HMAC_KEY, new SecureRandom()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SensitiveValueProtector(
                        base64Key(16, (byte) 0x43), HMAC_KEY, new SecureRandom()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SensitiveValueProtector(
                        ENCRYPTION_KEY, base64Key(16, (byte) 0x44), new SecureRandom()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> protector.encrypt(" ", "value"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> protector.encrypt("profile:wechat-id", " "));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> protector.decrypt("profile:wechat-id", new byte[] {2}));
        byte[] unsupportedVersionCiphertext = new byte[29];
        unsupportedVersionCiphertext[0] = 2;
        assertThatIllegalArgumentException()
                .isThrownBy(() -> protector.decrypt("profile:wechat-id", unsupportedVersionCiphertext));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> protector.hmac("consent:ip", " "));
    }

    private static String base64Key(int length, byte value) {
        byte[] key = new byte[length];
        Arrays.fill(key, value);
        return Base64.getEncoder().encodeToString(key);
    }
}
