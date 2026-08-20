package com.love.archive.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.identity.persistence.ActivationCodeEntity;
import com.love.archive.identity.persistence.UserAccountEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 敏感字段现在以明文列存储，密文防御性复制那一套已经不存在了。
 * 仍然要守住的是：实体不生成 toString，避免整行数据（含密码摘要、手机号、激活码）进日志。
 */
class LombokEntitySafetyTest {

    private static final List<Path> ENTITY_PACKAGES = List.of(
            Path.of("src/main/java/com/love/archive/admin/persistence"),
            Path.of("src/main/java/com/love/archive/identity/persistence"),
            Path.of("src/main/java/com/love/archive/guest/persistence"),
            Path.of("src/main/java/com/love/archive/consent/persistence"),
            Path.of("src/main/java/com/love/archive/payment/persistence"));

    @Test
    void entityStringRepresentationsDoNotExposeStoredSecrets() {
        AdminUserEntity admin = new AdminUserEntity();
        admin.setPasswordHash("admin-password-hash-secret");
        UserAccountEntity account = new UserAccountEntity();
        account.setPasswordHash("guest-password-hash-secret");
        account.setPhone("13800138000");
        ActivationCodeEntity code = new ActivationCodeEntity();
        code.setCode("LOVE-SECRET-CODE");

        assertThat(admin.toString()).doesNotContain("admin-password-hash-secret");
        assertThat(account.toString())
                .doesNotContain("guest-password-hash-secret")
                .doesNotContain("13800138000");
        assertThat(code.toString()).doesNotContain("LOVE-SECRET-CODE");
    }

    @Test
    void persistenceEntitiesDoNotUseDataOrToString() throws IOException {
        StringBuilder entities = new StringBuilder();
        for (Path packageRoot : ENTITY_PACKAGES) {
            entities.append(readTree(packageRoot));
        }

        assertThat(entities.toString())
                .doesNotContain("lombok.Data")
                .doesNotContain("@Data")
                .doesNotContain("lombok.ToString")
                .doesNotContain("@ToString");
    }

    private static String readTree(Path root) throws IOException {
        StringBuilder content = new StringBuilder();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                content.append(Files.readString(file)).append('\n');
            }
        }
        return content.toString();
    }
}
