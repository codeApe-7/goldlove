package com.love.archive.admin.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.identity.security.PasswordHasher;
import org.junit.jupiter.api.Test;

class AdminBootstrapRunnerTest {

    @Test
    void rejectsTheShippedPlaceholderPasswordBeforeDatabaseAccess() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties();
        properties.setUsername("operator");
        properties.setDisplayName("Operator");
        properties.setPassword("replace-with-a-strong-initial-admin-password");
        AdminUserMapper mapper = mock(AdminUserMapper.class);
        PasswordHasher hasher = mock(PasswordHasher.class);

        AdminBootstrapRunner runner = new AdminBootstrapRunner(properties, mapper, hasher);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("初始管理员密码不符合安全要求");
        verifyNoInteractions(mapper, hasher);
    }
}
