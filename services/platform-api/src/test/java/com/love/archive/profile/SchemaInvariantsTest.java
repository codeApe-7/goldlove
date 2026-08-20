package com.love.archive.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.love.archive.testsupport.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 数据库层不变量的守卫：这些约束不依赖应用代码，绕过服务层直接写库也必须被拦住。
 * 审核相关的表已经拆掉，剩下的不变量是字段定义身份、同意记录与审计日志的只追加性，
 * 以及运行时账号的最小权限。
 */
class SchemaInvariantsTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void migrationSeedsTheActiveDocumentAndCoreFieldDefinitions() {
        assertThat(jdbc.sql("SELECT count(*) FROM authorization_document WHERE status = 'ACTIVE'")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM profile_field_definition WHERE storage_kind = 'CORE'")
                .query(Integer.class).single()).isEqualTo(11);
        assertThat(jdbc.sql("""
                        SELECT data_type FROM profile_field_definition WHERE field_code = 'income_range'
                        """)
                .query(String.class).single()).isEqualTo("SINGLE_OPTION");
    }

    @Test
    void reviewAndManualPathTablesAreGone() {
        for (String table : new String[] {
                "profile_revision", "profile_revision_field_value", "profile_revision_photo",
                "profile_review_record", "activation_credential", "registration_token",
                "external_identity", "wechat_payment_order"}) {
            assertThat(tableExists(table))
                    .withFailMessage("表 %s 本应随重构一起拆除", table)
                    .isFalse();
        }
    }

    @Test
    void userAccountKeepsNoCiphertextColumns() {
        assertThat(columnExists("user_account", "phone")).isTrue();
        assertThat(columnExists("user_account", "phone_ciphertext")).isFalse();
        assertThat(columnExists("user_account", "phone_hmac")).isFalse();
        assertThat(columnExists("guest_profile", "wechat_id")).isTrue();
        assertThat(columnExists("guest_profile", "wechat_id_ciphertext")).isFalse();
        assertThat(columnExists("payment_record", "transaction_id")).isTrue();
        assertThat(columnExists("payment_record", "transaction_id_ciphertext")).isFalse();
    }

    @Test
    void phoneShapeIsEnforcedByTheDatabase() throws SQLException {
        try (Connection owner = ownerConnection()) {
            assertSqlRejected(
                    () -> execute(owner, """
                            INSERT INTO user_account (phone, password_hash)
                            VALUES ('1234', 'hash')
                            """),
                    "ck_user_account_phone");
        }
    }

    @Test
    void firstFieldValueInsertPermanentlyMarksTheDefinitionUsed() throws SQLException {
        try (Connection owner = ownerConnection()) {
            long accountId = insertAccount(owner, "13800138100");
            long profileId = insertProfile(owner, accountId);
            long definitionId = insertDynamicDefinition(owner, "invariant_text");

            execute(owner, """
                    INSERT INTO profile_field_value (guest_profile_id, field_definition_id, text_value)
                    VALUES (%d, %d, 'first-use')
                    """.formatted(profileId, definitionId));

            assertThat(queryBoolean(owner,
                    "SELECT ever_used FROM profile_field_definition WHERE id = " + definitionId))
                    .isTrue();
            // 用过之后身份不可再改，也不能把标记抹掉。
            assertSqlRejected(
                    () -> execute(owner, "UPDATE profile_field_definition SET field_code = 'renamed' WHERE id = "
                            + definitionId),
                    "used profile field definition identity is immutable");
            assertSqlRejected(
                    () -> execute(owner, "UPDATE profile_field_definition SET data_type = 'INTEGER' WHERE id = "
                            + definitionId),
                    "used profile field definition identity is immutable");
            assertSqlRejected(
                    () -> execute(owner, "UPDATE profile_field_definition SET ever_used = false WHERE id = "
                            + definitionId),
                    "profile field definition usage cannot be reset");
            assertSqlRejected(
                    () -> execute(owner, "UPDATE profile_field_definition SET storage_kind = 'CORE' WHERE id = "
                            + definitionId),
                    "storage kind is immutable");
        }
    }

    @Test
    void consentRecordsAndAuditLogAreAppendOnly() throws SQLException {
        try (Connection owner = ownerConnection()) {
            long accountId = insertAccount(owner, "13800138101");
            long documentId = queryLong(owner,
                    "SELECT id FROM authorization_document WHERE status = 'ACTIVE' LIMIT 1");
            long recordId = queryLong(owner, """
                    INSERT INTO authorization_record (
                        user_account_id, authorization_document_id, accepted,
                        accepted_at, effective_at, expires_at, source_page)
                    VALUES (%d, %d, TRUE, now(), now(), now() + interval '1 year', 'guest-register')
                    RETURNING id
                    """.formatted(accountId, documentId));

            assertSqlRejected(
                    () -> execute(owner, "UPDATE authorization_record SET source_page = 'x' WHERE id = " + recordId),
                    "authorization_record rows are immutable");
            assertSqlRejected(
                    () -> execute(owner, "DELETE FROM authorization_record WHERE id = " + recordId),
                    "authorization_record rows are immutable");

            long auditId = queryLong(owner, """
                    INSERT INTO audit_log (actor_type, actor_id, action, target_type, target_id)
                    VALUES ('SYSTEM', NULL, 'TEST', 'USER_ACCOUNT', %d) RETURNING id
                    """.formatted(accountId));
            assertSqlRejected(
                    () -> execute(owner, "UPDATE audit_log SET action = 'CHANGED' WHERE id = " + auditId),
                    "audit_log rows are immutable");
            assertSqlRejected(
                    () -> execute(owner, "DELETE FROM audit_log WHERE id = " + auditId),
                    "audit_log rows are immutable");
        }
    }

    @Test
    void runtimeRoleHasNoDeleteOnLedgerTablesAndNoDdl() throws SQLException {
        try (Connection runtime = runtimeConnection()) {
            assertThat(queryString(runtime, "SELECT current_user")).isEqualTo("archive_app");

            for (String table : new String[] {
                    "user_account", "activation_code", "payment_order", "payment_record",
                    "guest_profile", "authorization_record", "audit_log"}) {
                assertThat(hasPrivilege(runtime, table, "DELETE"))
                        .withFailMessage("archive_app 不应有 %s 的 DELETE 权限", table)
                        .isFalse();
            }
            // 草稿里的字段值与照片可以被覆盖或删除。
            assertThat(hasPrivilege(runtime, "profile_field_value", "DELETE")).isTrue();
            assertThat(hasPrivilege(runtime, "profile_photo", "DELETE")).isTrue();
            // 授权书由迁移维护，运行时只读。
            assertThat(hasPrivilege(runtime, "authorization_document", "INSERT")).isFalse();

            assertSqlRejected(
                    () -> execute(runtime, "CREATE TABLE runtime_should_not_create (id int)"),
                    "permission denied for schema public");
        }
    }

    private boolean tableExists(String table) {
        return jdbc.sql("SELECT to_regclass('public.' || :table) IS NOT NULL")
                .param("table", table)
                .query(Boolean.class)
                .single();
    }

    private boolean columnExists(String table, String column) {
        return jdbc.sql("""
                        SELECT count(*) > 0 FROM information_schema.columns
                        WHERE table_name = :table AND column_name = :column
                        """)
                .param("table", table)
                .param("column", column)
                .query(Boolean.class)
                .single();
    }

    private static long insertAccount(Connection owner, String phone) throws SQLException {
        return queryLong(owner, """
                INSERT INTO user_account (phone, password_hash) VALUES ('%s', 'test-hash') RETURNING id
                """.formatted(phone));
    }

    private static long insertProfile(Connection owner, long accountId) throws SQLException {
        return queryLong(owner, """
                INSERT INTO guest_profile (profile_no, user_account_id, status)
                VALUES (gen_random_uuid(), %d, 'DRAFT') RETURNING id
                """.formatted(accountId));
    }

    private static long insertDynamicDefinition(Connection owner, String fieldCode) throws SQLException {
        return queryLong(owner, """
                INSERT INTO profile_field_definition (
                    field_code, label, storage_kind, data_type, required, enabled, sort_order)
                VALUES ('%s', '不变量字段', 'DYNAMIC', 'TEXT', FALSE, TRUE, 900) RETURNING id
                """.formatted(fieldCode));
    }

    private static boolean hasPrivilege(Connection connection, String table, String privilege)
            throws SQLException {
        return queryBoolean(connection,
                "SELECT has_table_privilege('archive_app', '%s', '%s')".formatted(table, privilege));
    }

    private static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection runtimeConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "archive_app", "integration-runtime-only");
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static boolean queryBoolean(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static int execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private static void assertSqlRejected(SqlAction action, String messageFragment) {
        assertThatThrownBy(action::execute)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(messageFragment);
    }

    @FunctionalInterface
    private interface SqlAction {
        void execute() throws SQLException;
    }
}
