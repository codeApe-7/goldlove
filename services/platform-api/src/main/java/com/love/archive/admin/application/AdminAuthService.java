package com.love.archive.admin.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.admin.web.AdminSessionView;
import com.love.archive.common.web.ApiException;
import com.love.archive.identity.security.PasswordHasher;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordHasher passwordHasher;
    private final String dummyPasswordHash;

    public AdminAuthService(AdminUserMapper adminUserMapper, PasswordHasher passwordHasher) {
        this.adminUserMapper = adminUserMapper;
        this.passwordHasher = passwordHasher;
        char[] dummyPassword = "timing-equalization-only".toCharArray();
        try {
            this.dummyPasswordHash = passwordHasher.hash(dummyPassword);
        } finally {
            Arrays.fill(dummyPassword, '\0');
        }
    }

    @Transactional
    public AdminSessionView authenticate(String rawUsername, String rawPassword) {
        String username = rawUsername.trim();
        AdminUserEntity admin = adminUserMapper.selectOne(
                Wrappers.<AdminUserEntity>lambdaQuery().eq(AdminUserEntity::getUsername, username));

        char[] password = rawPassword.toCharArray();
        boolean passwordMatches;
        try {
            passwordMatches = passwordHasher.matches(
                    password,
                    admin == null ? dummyPasswordHash : admin.getPasswordHash());
        } finally {
            Arrays.fill(password, '\0');
        }

        if (admin == null || !passwordMatches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "用户名或密码错误");
        }
        if (admin.getStatus() != AdminStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_DISABLED", "管理员账号已停用");
        }

        OffsetDateTime now = OffsetDateTime.now();
        int updated = adminUserMapper.update(
                Wrappers.<AdminUserEntity>lambdaUpdate()
                        .eq(AdminUserEntity::getId, admin.getId())
                        .set(AdminUserEntity::getLastLoginAt, now)
                        .set(AdminUserEntity::getUpdatedAt, now));
        if (updated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ADMIN_UPDATE_CONFLICT", "管理员状态已发生变化，请重试");
        }
        return new AdminSessionView(admin.getId(), admin.getUsername(), admin.getDisplayName());
    }
}
