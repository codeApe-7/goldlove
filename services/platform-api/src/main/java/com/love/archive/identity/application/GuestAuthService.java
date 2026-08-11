package com.love.archive.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.web.ApiException;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.domain.PhoneNormalizer;
import com.love.archive.identity.persistence.ActivationCredentialEntity;
import com.love.archive.identity.persistence.ActivationCredentialMapper;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.identity.security.PhoneProtector;
import com.love.archive.identity.web.GuestSessionView;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestAuthService {

    private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");

    private final UserAccountMapper userAccountMapper;
    private final ActivationCredentialMapper activationCredentialMapper;
    private final AuditTrail auditTrail;
    private final PhoneNormalizer phoneNormalizer;
    private final PhoneProtector phoneProtector;
    private final PasswordHasher passwordHasher;
    private final String dummyPasswordHash;

    public GuestAuthService(
            UserAccountMapper userAccountMapper,
            ActivationCredentialMapper activationCredentialMapper,
            AuditTrail auditTrail,
            PhoneNormalizer phoneNormalizer,
            PhoneProtector phoneProtector,
            PasswordHasher passwordHasher) {
        this.userAccountMapper = userAccountMapper;
        this.activationCredentialMapper = activationCredentialMapper;
        this.auditTrail = auditTrail;
        this.phoneNormalizer = phoneNormalizer;
        this.phoneProtector = phoneProtector;
        this.passwordHasher = passwordHasher;
        char[] dummy = "guest-login-timing-only".toCharArray();
        try {
            this.dummyPasswordHash = passwordHasher.hash(dummy);
        } finally {
            Arrays.fill(dummy, '\0');
        }
    }

    @Transactional
    public GuestSessionView activate(
            String rawPhone,
            String initialCredential,
            String newPassword,
            String requestId) {
        validateNewPassword(newPassword, initialCredential);
        UserAccountEntity account = findByPhoneForActivation(rawPhone);
        boolean pending = account != null && account.getStatus() == AccountStatus.PAID_PENDING_ACTIVATION;
        ActivationCredentialEntity credential = pending
                ? activationCredentialMapper.selectOne(
                        Wrappers.<ActivationCredentialEntity>lambdaQuery()
                                .eq(ActivationCredentialEntity::getUserAccountId, account.getId())
                                .isNull(ActivationCredentialEntity::getConsumedAt))
                : null;

        OffsetDateTime now = OffsetDateTime.now();
        char[] initialChars = initialCredential.toCharArray();
        boolean initialMatches;
        try {
            initialMatches = passwordHasher.matches(
                    initialChars,
                    credential == null ? dummyPasswordHash : credential.getCredentialHash());
        } finally {
            Arrays.fill(initialChars, '\0');
        }
        if (!pending
                || credential == null
                || !credential.getExpiresAt().isAfter(now)
                || !initialMatches) {
            throw invalidActivation();
        }

        char[] passwordChars = newPassword.toCharArray();
        String newPasswordHash;
        try {
            newPasswordHash = passwordHasher.hash(passwordChars);
        } finally {
            Arrays.fill(passwordChars, '\0');
        }

        int accountUpdated = userAccountMapper.update(
                Wrappers.<UserAccountEntity>lambdaUpdate()
                        .eq(UserAccountEntity::getId, account.getId())
                        .eq(UserAccountEntity::getStatus, AccountStatus.PAID_PENDING_ACTIVATION)
                        .set(UserAccountEntity::getPasswordHash, newPasswordHash)
                        .set(UserAccountEntity::getStatus, AccountStatus.ACTIVE)
                        .set(UserAccountEntity::getActivatedAt, now)
                        .set(UserAccountEntity::getUpdatedAt, now));
        int credentialUpdated = activationCredentialMapper.update(
                Wrappers.<ActivationCredentialEntity>lambdaUpdate()
                        .eq(ActivationCredentialEntity::getId, credential.getId())
                        .isNull(ActivationCredentialEntity::getConsumedAt)
                        .set(ActivationCredentialEntity::getConsumedAt, now));
        if (accountUpdated != 1 || credentialUpdated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVATION_CONFLICT", "账号状态已变化，请重试");
        }

        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.GUEST,
                account.getId(),
                "GUEST_ACCOUNT_ACTIVATED",
                "USER_ACCOUNT",
                account.getId(),
                requestId,
                "{}",
                now));
        return new GuestSessionView(account.getId(), AccountStatus.ACTIVE, null, 0L);
    }

    @Transactional
    public GuestSessionView authenticate(String rawPhone, String rawPassword) {
        UserAccountEntity account = findByPhoneForLogin(rawPhone);

        char[] password = rawPassword.toCharArray();
        boolean matches;
        try {
            matches = passwordHasher.matches(
                    password,
                    account == null || account.getPasswordHash() == null
                            ? dummyPasswordHash
                            : account.getPasswordHash());
        } finally {
            Arrays.fill(password, '\0');
        }
        if (account == null || !matches) {
            throw invalidLogin();
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_INACTIVE", "账号尚未激活或已停用");
        }

        OffsetDateTime now = OffsetDateTime.now();
        int updated = userAccountMapper.update(
                Wrappers.<UserAccountEntity>lambdaUpdate()
                        .eq(UserAccountEntity::getId, account.getId())
                        .eq(UserAccountEntity::getStatus, AccountStatus.ACTIVE)
                        .set(UserAccountEntity::getLastLoginAt, now)
                        .set(UserAccountEntity::getUpdatedAt, now));
        if (updated != 1) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_INACTIVE", "账号尚未激活或已停用");
        }
        return new GuestSessionView(account.getId(), account.getStatus(), null, 0L);
    }

    @Transactional(readOnly = true)
    public GuestSessionView getSession(long accountId) {
        UserAccountEntity account = userAccountMapper.selectById(accountId);
        if (account == null || account.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_INACTIVE", "账号尚未激活或已停用");
        }
        return new GuestSessionView(account.getId(), account.getStatus(), null, 0L);
    }

    private UserAccountEntity findByPhoneForActivation(String rawPhone) {
        try {
            String phone = phoneNormalizer.normalize(rawPhone);
            return userAccountMapper.selectOne(Wrappers.<UserAccountEntity>lambdaQuery()
                    .eq(UserAccountEntity::getPhoneHmac, phoneProtector.searchHash(phone))
                    .last("FOR UPDATE"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private UserAccountEntity findByPhoneForLogin(String rawPhone) {
        try {
            String phone = phoneNormalizer.normalize(rawPhone);
            return userAccountMapper.selectOne(Wrappers.<UserAccountEntity>lambdaQuery()
                    .eq(UserAccountEntity::getPhoneHmac, phoneProtector.searchHash(phone)));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void validateNewPassword(String password, String initialCredential) {
        if (password == null
                || password.length() < 12
                || password.length() > 128
                || !HAS_LETTER.matcher(password).matches()
                || !HAS_DIGIT.matcher(password).matches()
                || password.equals(initialCredential)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_POLICY_VIOLATION",
                    "密码需为 12 至 128 位并同时包含字母和数字");
        }
    }

    private static ApiException invalidLogin() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "手机号或密码错误");
    }

    private static ApiException invalidActivation() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "ACTIVATION_INVALID", "手机号或初始凭证无效");
    }
}
