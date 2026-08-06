package com.love.archive.admin.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.identity.security.PasswordHasher;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "app.admin.bootstrap", name = "enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Set<String> FORBIDDEN_PASSWORDS = Set.of("password", "changeme", "admin123", "123456");

    private final AdminBootstrapProperties properties;
    private final AdminUserMapper adminUserMapper;
    private final PasswordHasher passwordHasher;

    public AdminBootstrapRunner(
            AdminBootstrapProperties properties,
            AdminUserMapper adminUserMapper,
            PasswordHasher passwordHasher) {
        this.properties = properties;
        this.adminUserMapper = adminUserMapper;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String username = requireText(properties.getUsername(), "初始管理员用户名不能为空").trim();
        String displayName = requireText(properties.getDisplayName(), "初始管理员显示名不能为空").trim();
        String rawPassword = requireText(properties.getPassword(), "初始管理员密码不能为空");
        if (rawPassword.length() < 12 || FORBIDDEN_PASSWORDS.contains(rawPassword.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("初始管理员密码不符合安全要求");
        }

        Long existing = adminUserMapper.selectCount(
                Wrappers.<AdminUserEntity>lambdaQuery().eq(AdminUserEntity::getUsername, username));
        if (existing > 0) {
            return;
        }

        char[] password = rawPassword.toCharArray();
        String passwordHash;
        try {
            passwordHash = passwordHasher.hash(password);
        } finally {
            Arrays.fill(password, '\0');
        }

        OffsetDateTime now = OffsetDateTime.now();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername(username);
        admin.setDisplayName(displayName);
        admin.setPasswordHash(passwordHash);
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminUserMapper.insert(admin);
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}
