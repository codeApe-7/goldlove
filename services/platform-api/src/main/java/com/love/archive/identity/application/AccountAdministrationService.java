package com.love.archive.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.web.ApiException;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 管理员停用 / 启用访客账号。
 *
 * <p>停用是账号级别的动作：{@code GuestStatusInterceptor} 每个请求都会查账号是否 ACTIVE，
 * 所以状态一改，该手机号下一次调接口就会被挡住，不需要额外去踢 sa-token 的会话。
 * 后台仍然能查看被停用账号的档案——停用是「不让他再改」，不是「不让我们再看」。</p>
 *
 * <p>CLOSED 是用户自己注销的终态，不接受管理员改回来：那需要的是重新注册，
 * 而不是把一个已注销的身份悄悄复活。</p>
 */
@Service
@RequiredArgsConstructor
public class AccountAdministrationService {

    private static final int MAX_REASON_LENGTH = 200;

    private final UserAccountMapper userAccountMapper;
    private final AuditTrail auditTrail;
    private final ObjectMapper objectMapper;

    @Transactional
    public AccountStatusView suspend(long accountId, String reason, long adminId, String requestId) {
        return changeStatus(accountId, AccountStatus.SUSPENDED, reason, adminId, requestId);
    }

    @Transactional
    public AccountStatusView activate(long accountId, String reason, long adminId, String requestId) {
        return changeStatus(accountId, AccountStatus.ACTIVE, reason, adminId, requestId);
    }

    private AccountStatusView changeStatus(
            long accountId,
            AccountStatus target,
            String reason,
            long adminId,
            String requestId) {
        UserAccountEntity account = lockAccount(accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "ACCOUNT_CLOSED", "账号已注销，不能再改状态");
        }
        if (account.getStatus() == target) {
            // 幂等：重复点一次不该报错，也不该多写一条审计。
            return new AccountStatusView(accountId, target.databaseValue());
        }

        OffsetDateTime now = OffsetDateTime.now();
        int updated = userAccountMapper.update(
                Wrappers.<UserAccountEntity>lambdaUpdate()
                        .eq(UserAccountEntity::getId, accountId)
                        .set(UserAccountEntity::getStatus, target)
                        .set(UserAccountEntity::getUpdatedAt, now));
        if (updated != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "ACCOUNT_STATUS_CONFLICT", "账号状态已变化，请重试");
        }

        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.ADMIN,
                adminId,
                target == AccountStatus.SUSPENDED ? "ACCOUNT_SUSPENDED" : "ACCOUNT_ACTIVATED",
                "USER_ACCOUNT",
                accountId,
                requestId,
                metadata(account.getStatus(), target, reason),
                now));
        return new AccountStatusView(accountId, target.databaseValue());
    }

    private UserAccountEntity lockAccount(long accountId) {
        UserAccountEntity account = userAccountMapper.selectOne(
                Wrappers.<UserAccountEntity>lambdaQuery()
                        .eq(UserAccountEntity::getId, accountId)
                        .last("FOR UPDATE"));
        if (account == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号不存在");
        }
        return account;
    }

    private String metadata(AccountStatus from, AccountStatus to, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("from", from.databaseValue());
        metadata.put("to", to.databaseValue());
        metadata.put("reason", normalizeReason(reason));
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException error) {
            return "{\"from\":\"" + from.databaseValue() + "\",\"to\":\"" + to.databaseValue() + "\"}";
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.strip();
        return trimmed.length() <= MAX_REASON_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_REASON_LENGTH);
    }
}
