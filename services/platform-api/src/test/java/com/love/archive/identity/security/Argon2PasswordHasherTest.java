package com.love.archive.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class Argon2PasswordHasherTest {

    private final PasswordHasher hasher = new Argon2PasswordHasher(
            new SecureRandom(),
            4_096,
            2,
            1,
            16,
            32);

    @Test
    void hashesAndMatchesPasswordWithArgon2id() {
        String encoded = hasher.hash("A-safe-password-2026".toCharArray());

        assertThat(encoded).startsWith("$argon2id$v=19$m=4096,t=2,p=1$");
        assertThat(hasher.matches("A-safe-password-2026".toCharArray(), encoded)).isTrue();
        assertThat(hasher.matches("wrong-password".toCharArray(), encoded)).isFalse();
    }

    @Test
    void usesAUniqueSaltForEveryHash() {
        char[] password = "A-safe-password-2026".toCharArray();

        assertThat(hasher.hash(password)).isNotEqualTo(hasher.hash(password));
    }

    @Test
    void rejectsMalformedEncodedHashWithoutThrowing() {
        assertThat(hasher.matches("password".toCharArray(), "not-an-argon2-hash")).isFalse();
        assertThat(hasher.matches("password".toCharArray(), null)).isFalse();
    }
}
