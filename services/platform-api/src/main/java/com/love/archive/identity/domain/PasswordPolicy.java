package com.love.archive.identity.domain;

import com.love.archive.common.web.ApiException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/** 访客密码策略。 */
public final class PasswordPolicy {

    private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");
    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null
                || password.length() < MIN_LENGTH
                || password.length() > MAX_LENGTH
                || !HAS_LETTER.matcher(password).matches()
                || !HAS_DIGIT.matcher(password).matches()) {
            throw violation();
        }
    }

    private static ApiException violation() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "PASSWORD_POLICY_VIOLATION",
                "密码需为 12 至 128 位并同时包含字母和数字");
    }
}
