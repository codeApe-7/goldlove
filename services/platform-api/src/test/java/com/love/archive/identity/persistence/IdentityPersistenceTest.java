package com.love.archive.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.testsupport.PostgresIntegrationTest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class IdentityPersistenceTest extends PostgresIntegrationTest {

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void flywayMigratesSchemaAndMybatisPlusPersistsAccount() {
        OffsetDateTime now = OffsetDateTime.now();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("persistence-test-admin");
        admin.setDisplayName("Persistence Test Admin");
        admin.setPasswordHash("$argon2id$test-only-placeholder");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        assertThat(adminUserMapper.insert(admin)).isOne();

        UserAccountEntity account = new UserAccountEntity();
        account.setPhoneCiphertext(new byte[] {1, 2, 3});
        account.setPhoneHmac("phone-hmac-for-persistence-test");
        account.setStatus(AccountStatus.PAID_PENDING_ACTIVATION);
        account.setCreatedByAdminId(admin.getId());
        account.setCreatedAt(now);
        account.setUpdatedAt(now);

        assertThat(userAccountMapper.insert(account)).isOne();
        assertThat(account.getId()).isPositive();

        UserAccountEntity reloaded = userAccountMapper.selectOne(
                Wrappers.<UserAccountEntity>lambdaQuery()
                        .eq(UserAccountEntity::getPhoneHmac, account.getPhoneHmac()));

        assertThat(reloaded.getId()).isEqualTo(account.getId());
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.PAID_PENDING_ACTIVATION);
        assertThat(reloaded.getPhoneCiphertext()).containsExactly(1, 2, 3);
        assertThat(tableCount("user_account")).isOne();
        assertThat(tableCount("admin_user")).isOne();
        assertThat(tableCount("activation_credential")).isOne();
        assertThat(tableCount("external_identity")).isOne();
        assertThat(tableCount("payment_record")).isOne();
        assertThat(tableCount("audit_log")).isOne();
    }

    @Test
    void runtimeRoleCanAppendButCannotMutateAuditOrCreateTables() {
        assertThat(jdbcClient.sql("SELECT current_user").query(String.class).single())
                .isEqualTo("archive_app");
        assertThat(hasTablePrivilege("audit_log", "SELECT")).isTrue();
        assertThat(hasTablePrivilege("audit_log", "INSERT")).isTrue();
        assertThat(hasTablePrivilege("audit_log", "UPDATE")).isFalse();
        assertThat(hasTablePrivilege("audit_log", "DELETE")).isFalse();
        assertThat(hasTablePrivilege("audit_log", "TRUNCATE")).isFalse();
        assertThat(jdbcClient.sql("SELECT has_schema_privilege(current_user, 'public', 'CREATE')")
                .query(Boolean.class)
                .single()).isFalse();
    }

    private boolean hasTablePrivilege(String table, String privilege) {
        return jdbcClient.sql("SELECT has_table_privilege(current_user, :table, :privilege)")
                .param("table", table)
                .param("privilege", privilege)
                .query(Boolean.class)
                .single();
    }

    private long tableCount(String tableName) {
        return jdbcClient.sql("""
                        SELECT count(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name = :tableName
                        """)
                .param("tableName", tableName)
                .query(Long.class)
                .single();
    }
}
