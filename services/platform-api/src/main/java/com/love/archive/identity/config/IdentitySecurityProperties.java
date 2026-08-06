package com.love.archive.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.identity.security")
public class IdentitySecurityProperties {

    private String phoneEncryptionKey;
    private String phoneSearchKey;
    private final Argon2 argon2 = new Argon2();
    private int initialCredentialLength = 20;
    private Duration activationTtl = Duration.ofDays(7);

    public String getPhoneEncryptionKey() {
        return phoneEncryptionKey;
    }

    public void setPhoneEncryptionKey(String phoneEncryptionKey) {
        this.phoneEncryptionKey = phoneEncryptionKey;
    }

    public String getPhoneSearchKey() {
        return phoneSearchKey;
    }

    public void setPhoneSearchKey(String phoneSearchKey) {
        this.phoneSearchKey = phoneSearchKey;
    }

    public Argon2 getArgon2() {
        return argon2;
    }

    public int getInitialCredentialLength() {
        return initialCredentialLength;
    }

    public void setInitialCredentialLength(int initialCredentialLength) {
        this.initialCredentialLength = initialCredentialLength;
    }

    public Duration getActivationTtl() {
        return activationTtl;
    }

    public void setActivationTtl(Duration activationTtl) {
        this.activationTtl = activationTtl;
    }

    public static class Argon2 {

        private int memoryKiB = 65_536;
        private int iterations = 3;
        private int parallelism = 1;
        private int saltLength = 16;
        private int hashLength = 32;

        public int getMemoryKiB() {
            return memoryKiB;
        }

        public void setMemoryKiB(int memoryKiB) {
            this.memoryKiB = memoryKiB;
        }

        public int getIterations() {
            return iterations;
        }

        public void setIterations(int iterations) {
            this.iterations = iterations;
        }

        public int getParallelism() {
            return parallelism;
        }

        public void setParallelism(int parallelism) {
            this.parallelism = parallelism;
        }

        public int getSaltLength() {
            return saltLength;
        }

        public void setSaltLength(int saltLength) {
            this.saltLength = saltLength;
        }

        public int getHashLength() {
            return hashLength;
        }

        public void setHashLength(int hashLength) {
            this.hashLength = hashLength;
        }
    }
}
