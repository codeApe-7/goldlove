package com.love.archive.identity.domain;

import java.util.regex.Pattern;

public final class PhoneNormalizer {

    private static final Pattern MAINLAND_MOBILE = Pattern.compile("1[3-9]\\d{9}");

    public String normalize(String rawPhone) {
        if (rawPhone == null) {
            throw invalidPhone();
        }

        String normalized = rawPhone.replaceAll("[\\s-]", "");
        if (normalized.startsWith("+86")) {
            normalized = normalized.substring(3);
        } else if (normalized.startsWith("0086")) {
            normalized = normalized.substring(4);
        } else if (normalized.startsWith("86") && normalized.length() == 13) {
            normalized = normalized.substring(2);
        }

        if (!MAINLAND_MOBILE.matcher(normalized).matches()) {
            throw invalidPhone();
        }
        return normalized;
    }

    private IllegalArgumentException invalidPhone() {
        return new IllegalArgumentException("手机号格式不正确");
    }
}

