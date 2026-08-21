package com.love.archive.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.common.web.ApiException;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.domain.PhoneNormalizer;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.identity.web.GuestSessionView;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 访客登录与会话查询。注册走 {@link SelfRegistrationService}，没有激活环节。
 */
@Service
public class GuestAuthService {

    private final UserAccountMapper userAccountMapper;
    private final PhoneNormalizer phoneNormalizer;
    private final PasswordHasher passwordHasher;
    /** 手机号不存在时也跑一次同样开销的比对，避免用响应时间探测账号是否存在。 */
    private final String dummyPasswordHash;

    public GuestAuthService(
            UserAccountMapper userAccountMapper,
            PhoneNormalizer phoneNormalizer,
            PasswordHasher passwordHasher) {
        this.userAccountMapper = userAccountMapper;
        this.phoneNormalizer = phoneNormalizer;
        this.passwordHasher = passwordHasher;
        char[] dummy = "guest-login-timing-only".toCharArray();
        try {
            this.dummyPasswordHash = passwordHasher.hash(dummy);
        } finally {
            Arrays.fill(dummy, '\0');
        }
    }

    @Transactional
    public GuestSessionView authenticate(String rawPhone, String rawPassword) {
        UserAccountEntity account = findByPhone(rawPhone);

        char[] password = rawPassword.toCharArray();
        boolean matches;
        try {
            matches = passwordHasher.matches(
                    password,
                    account == null ? dummyPasswordHash : account.getPasswordHash());
        } finally {
            Arrays.fill(password, '\0');
        }
        if (account == null || !matches) {
            throw invalidLogin();
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw inactive();
        }

        OffsetDateTime now = OffsetDateTime.now();
        int updated = userAccountMapper.update(
                Wrappers.<UserAccountEntity>lambdaUpdate()
                        .eq(UserAccountEntity::getId, account.getId())
                        .eq(UserAccountEntity::getStatus, AccountStatus.ACTIVE)
                        .set(UserAccountEntity::getLastLoginAt, now)
                        .set(UserAccountEntity::getUpdatedAt, now));
        if (updated != 1) {
            throw inactive();
        }
        return new GuestSessionView(
                account.getId(), account.getStatus(), account.getMembershipTier(), null, 0L);
    }

    @Transactional(readOnly = true)
    public GuestSessionView getSession(long accountId) {
        UserAccountEntity account = userAccountMapper.selectById(accountId);
        if (account == null || account.getStatus() != AccountStatus.ACTIVE) {
            throw inactive();
        }
        return new GuestSessionView(
                account.getId(), account.getStatus(), account.getMembershipTier(), null, 0L);
    }

    private UserAccountEntity findByPhone(String rawPhone) {
        try {
            return userAccountMapper.selectOne(Wrappers.<UserAccountEntity>lambdaQuery()
                    .eq(UserAccountEntity::getPhone, phoneNormalizer.normalize(rawPhone)));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ApiException invalidLogin() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "手机号或密码错误");
    }

    private static ApiException inactive() {
        return new ApiException(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_INACTIVE", "账号已停用");
    }
}
