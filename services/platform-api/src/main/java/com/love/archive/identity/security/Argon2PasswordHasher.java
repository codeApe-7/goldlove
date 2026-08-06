package com.love.archive.identity.security;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

public final class Argon2PasswordHasher implements PasswordHasher {

    private static final int ARGON2_VERSION = 19;

    private final SecureRandom secureRandom;
    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;
    private final int saltLength;
    private final int hashLength;

    public Argon2PasswordHasher(
            SecureRandom secureRandom,
            int memoryKiB,
            int iterations,
            int parallelism,
            int saltLength,
            int hashLength) {
        this.secureRandom = java.util.Objects.requireNonNull(secureRandom, "secureRandom");
        requireRange(memoryKiB, 1_024, 1_048_576, "Argon2 内存参数不正确");
        requireRange(iterations, 1, 10, "Argon2 迭代参数不正确");
        requireRange(parallelism, 1, 16, "Argon2 并行参数不正确");
        requireRange(saltLength, 16, 64, "Argon2 盐长度不正确");
        requireRange(hashLength, 16, 64, "Argon2 摘要长度不正确");
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
        this.saltLength = saltLength;
        this.hashLength = hashLength;
    }

    @Override
    public String hash(char[] password) {
        requirePassword(password);
        byte[] salt = new byte[saltLength];
        secureRandom.nextBytes(salt);
        byte[] hash = calculate(password, salt, memoryKiB, iterations, parallelism, hashLength);
        try {
            Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
            return "$argon2id$v=" + ARGON2_VERSION
                    + "$m=" + memoryKiB + ",t=" + iterations + ",p=" + parallelism
                    + "$" + encoder.encodeToString(salt)
                    + "$" + encoder.encodeToString(hash);
        } finally {
            Arrays.fill(hash, (byte) 0);
            Arrays.fill(salt, (byte) 0);
        }
    }

    @Override
    public boolean matches(char[] password, String encodedHash) {
        if (password == null || password.length == 0 || encodedHash == null) {
            return false;
        }
        try {
            ParsedHash parsed = ParsedHash.parse(encodedHash);
            byte[] actual = calculate(
                    password,
                    parsed.salt(),
                    parsed.memoryKiB(),
                    parsed.iterations(),
                    parsed.parallelism(),
                    parsed.hash().length);
            try {
                return MessageDigest.isEqual(actual, parsed.hash());
            } finally {
                Arrays.fill(actual, (byte) 0);
                Arrays.fill(parsed.hash(), (byte) 0);
                Arrays.fill(parsed.salt(), (byte) 0);
            }
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] calculate(
            char[] password,
            byte[] salt,
            int memory,
            int iterationCount,
            int lanes,
            int outputLength) {
        requireRange(memory, 1_024, 1_048_576, "Argon2 内存参数不正确");
        requireRange(iterationCount, 1, 10, "Argon2 迭代参数不正确");
        requireRange(lanes, 1, 16, "Argon2 并行参数不正确");
        requireRange(outputLength, 16, 64, "Argon2 摘要长度不正确");
        byte[] passwordBytes = utf8(password);
        byte[] output = new byte[outputLength];
        try {
            Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withMemoryAsKB(memory)
                    .withIterations(iterationCount)
                    .withParallelism(lanes)
                    .withSalt(salt)
                    .build();
            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(parameters);
            generator.generateBytes(passwordBytes, output);
            return output;
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    private static byte[] utf8(char[] characters) {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(characters));
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        if (buffer.hasArray()) {
            Arrays.fill(buffer.array(), (byte) 0);
        }
        return bytes;
    }

    private static void requirePassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("密码不能为空");
        }
    }

    private static void requireRange(int value, int minimum, int maximum, String message) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(message);
        }
    }

    private record ParsedHash(int memoryKiB, int iterations, int parallelism, byte[] salt, byte[] hash) {

        private static ParsedHash parse(String encoded) {
            String[] parts = encoded.split("\\$", -1);
            if (parts.length != 6
                    || !parts[0].isEmpty()
                    || !"argon2id".equals(parts[1])
                    || !"v=19".equals(parts[2])) {
                throw new IllegalArgumentException("Argon2 摘要格式不正确");
            }

            Map<String, Integer> parameters = new HashMap<>();
            for (String parameter : parts[3].split(",")) {
                String[] keyValue = parameter.split("=", -1);
                if (keyValue.length != 2) {
                    throw new IllegalArgumentException("Argon2 参数格式不正确");
                }
                parameters.put(keyValue[0], Integer.parseInt(keyValue[1]));
            }
            if (!parameters.keySet().equals(java.util.Set.of("m", "t", "p"))) {
                throw new IllegalArgumentException("Argon2 参数不完整");
            }
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[4]);
            byte[] hash = decoder.decode(parts[5]);
            if (salt.length < 16 || salt.length > 64) {
                throw new IllegalArgumentException("Argon2 盐长度不正确");
            }
            return new ParsedHash(parameters.get("m"), parameters.get("t"), parameters.get("p"), salt, hash);
        }
    }
}

