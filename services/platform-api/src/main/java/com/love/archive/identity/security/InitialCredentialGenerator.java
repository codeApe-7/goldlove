package com.love.archive.identity.security;

import java.security.SecureRandom;

public final class InitialCredentialGenerator {

    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private final SecureRandom secureRandom;
    private final int length;

    public InitialCredentialGenerator(SecureRandom secureRandom, int length) {
        if (length < 16) {
            throw new IllegalArgumentException("初始凭证长度不能少于 16 位");
        }
        this.secureRandom = java.util.Objects.requireNonNull(secureRandom, "secureRandom");
        this.length = length;
    }

    public String generate() {
        char[] value = new char[length];
        for (int index = 0; index < length; index++) {
            value[index] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
        }
        return new String(value);
    }
}

