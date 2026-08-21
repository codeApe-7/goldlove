package com.love.archive.identity.security;

import java.security.SecureRandom;

/**
 * 激活码格式 {@code LOVE-XXXX-XXXX-XXXX}。字母表剔除了 I/L/O/U 与 0/1，
 * 避免用户手抄时把 0 和 O、1 和 I/l 弄混。
 */
public final class ActivationCodeGenerator {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final String PREFIX = "LOVE";
    private static final int GROUPS = 3;
    private static final int GROUP_LENGTH = 4;

    private final SecureRandom secureRandom;

    public ActivationCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        StringBuilder code = new StringBuilder(PREFIX);
        for (int group = 0; group < GROUPS; group++) {
            code.append('-');
            for (int position = 0; position < GROUP_LENGTH; position++) {
                code.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
            }
        }
        return code.toString();
    }

    /** 用户输入允许小写与多余空格，比对前先规整。 */
    public static String normalize(String rawCode) {
        if (rawCode == null) {
            return "";
        }
        return rawCode.strip().replaceAll("\\s", "").toUpperCase(java.util.Locale.ROOT);
    }
}
