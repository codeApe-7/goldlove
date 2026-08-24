package com.love.archive.identity.domain;

import com.love.archive.common.web.ApiException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * 访客密码策略：8–128 位，且同时含字母与数字。
 *
 * <p>下限从 12 降到 8 是产品决定。这一档在离线爆破面前明显更弱，
 * 挡着它的是别处的两层：Argon2id 摘要（64 MiB / 3 轮，单次校验就很贵）
 * 与登录侧的 Redis 限流（账号 5 次 / 15 分钟、客户端 30 次 / 1 分钟）。
 * 动这个常量前先确认那两层还在。</p>
 *
 * <p><b>改这里必须同步改 {@code apps/guest-app/src/validators/registration.ts}
 * 与注册页的输入框提示。</b>前端那份只是让用户少跑一趟网络，
 * 但两边文案不一致时用户会看到「填了前端说可以的密码，后端却拒绝」。</p>
 */
public final class PasswordPolicy {

    private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");
    private static final int MIN_LENGTH = 8;
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
                "密码需为 8 至 128 位并同时包含字母和数字");
    }
}
