package com.love.archive.identity.security;

import com.love.archive.common.web.ApiException;
import com.love.archive.identity.config.AuthenticationRateLimitProperties;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationAttemptLimiter {

    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redis;
    private final PhoneProtector keyProtector;
    private final int accountMaxAttempts;
    private final Duration accountWindow;
    private final int clientMaxAttempts;
    private final Duration clientWindow;

    public AuthenticationAttemptLimiter(
            StringRedisTemplate redis,
            PhoneProtector keyProtector,
            AuthenticationRateLimitProperties properties) {
        this.redis = redis;
        this.keyProtector = keyProtector;
        this.accountMaxAttempts = requirePositive(properties.getAccountMaxAttempts(), "账号限流次数必须大于零");
        this.accountWindow = requirePositive(properties.getAccountWindow(), "账号限流窗口必须大于零");
        this.clientMaxAttempts = requirePositive(properties.getClientMaxAttempts(), "客户端限流次数必须大于零");
        this.clientWindow = requirePositive(properties.getClientWindow(), "客户端限流窗口必须大于零");
    }

    public void checkAndConsume(String flow, String accountIdentifier, String clientAddress) {
        String accountKey = accountKey(flow, accountIdentifier);
        String clientKey = clientKey(flow, clientAddress);
        try {
            if (consume(clientKey, clientWindow) > clientMaxAttempts
                    || consume(accountKey, accountWindow) > accountMaxAttempts) {
                throw rateLimited();
            }
        } catch (DataAccessException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_SERVICE_UNAVAILABLE",
                    "认证服务暂时不可用，请稍后重试");
        }
    }

    public void resetAccount(String flow, String accountIdentifier) {
        try {
            redis.delete(accountKey(flow, accountIdentifier));
        } catch (DataAccessException ignored) {
            // A successful login must not be turned into a failure by best-effort counter cleanup.
        }
    }

    private long consume(String key, Duration window) {
        Long current = redis.execute(CONSUME_SCRIPT, List.of(key), Long.toString(window.toMillis()));
        if (current == null) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_SERVICE_UNAVAILABLE",
                    "认证服务暂时不可用，请稍后重试");
        }
        return current;
    }

    private String accountKey(String flow, String identifier) {
        return "auth:attempt:" + flow + ":account:"
                + keyProtector.searchHash(normalize(identifier));
    }

    private String clientKey(String flow, String clientAddress) {
        return "auth:attempt:" + flow + ":client:"
                + keyProtector.searchHash(normalize(clientAddress));
    }

    private static String normalize(String value) {
        return value == null ? "unknown" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static int requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String message) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED", "尝试次数过多，请稍后重试");
    }
}
