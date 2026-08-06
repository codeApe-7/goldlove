package com.love.archive.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InitialCredentialGeneratorTest {

    private final InitialCredentialGenerator generator = new InitialCredentialGenerator(new SecureRandom(), 20);

    @Test
    void generatesReadableHighEntropyCredential() {
        assertThat(generator.generate())
                .hasSize(20)
                .matches("[ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789]{20}");
    }

    @Test
    void doesNotRepeatCredentialsInARepresentativeBatch() {
        Set<String> credentials = new HashSet<>();

        for (int i = 0; i < 1_000; i++) {
            credentials.add(generator.generate());
        }

        assertThat(credentials).hasSize(1_000);
    }
}
