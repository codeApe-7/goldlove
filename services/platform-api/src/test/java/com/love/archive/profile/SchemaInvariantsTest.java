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
                .query(Integer.class).single()).isEqualTo(9);
        assertThat(jdbc.sql("""
                        SELECT data_type FROM profile_field_definition WHERE field_code = 'income_range'
                        """)
                .query(String.class).single()).isEqualTo("SINGLE_OPTION");
    }

    @Test
    void migrationAlignsFieldOptionsWithTheDesignSpec() {
        // V2 清空全部用户数据后重建字段定义，学历与职业由自由文本改成固定选项集。
        assertThat(dataTypeOf("education")).isEqualTo("SINGLE_OPTION");
        assertThat(dataTypeOf("occupation")).isEqualTo("SINGLE_OPTION");
        assertThat(optionsOf("education")).contains("博士及以上", "硕士研究生", "大学本科", "大专");
        // V3 曾把职业细化到 47 个岗位，V4 改回「职业范围」粒度（规范 3.5 的行业档）。
        assertThat(optionsOf("occupation"))
                .contains("互联网 / IT", "金融 / 投资", "教育 / 培训", "其他行业")
                .doesNotContain("产品经理", "前端开发工程师", "数据分析师");

        // 三级联动是前端录入方式，所在城市仍按文本存回。
        assertThat(dataTypeOf("city")).isEqualTo("TEXT");

        assertThat(optionsOf("gender")).contains("男", "女", "不公开");

        // 年薪档位本身敏感，「保密」不在规范图 6 档里但必须保留，前端选中后不展示具体区间。
        assertThat(optionsOf("income_range"))
                .contains("20万以下", "20万-30万", "30万-50万", "50万-80万", "80万-120万", "120万以上", "保密")
                .doesNotContain("小于10万", "10-20万", "20-30万", "50-100万");

        // CORE 字段的值写在 guest_profile 的具名列里，从不产生 profile_field_value 行，
        // 所以永远不会被标记为用过——这是「改得动 data_type」的前提。
        // 只断言 CORE，因为本类其他用例会造出用过的 DYNAMIC 字段。
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM profile_field_definition
                        WHERE storage_kind = 'CORE' AND ever_used
                        """)
                .query(Integer.class).single()).isZero();
    }

    /**
     * V8 把档案收窄：只收年龄，不收出生日期；抖音只留账号本身。
     *
     * <p>三列都是「能直接指认到人」的信息，而档案实际用到的只有「多大」和「怎么联系」。
     * 断言列**不存在**而不只是断言不再读它：留着列，下一个人加个 SELECT * 就又把它捡回来了。</p>
     */
    @Test
    void theProfileOnlyKeepsAgeAndTheDouyinAccountItself() {
        assertThat(columnExists("guest_profile", "age")).isTrue();
        assertThat(columnExists("guest_profile", "birth_date")).isFalse();
        assertThat(columnExists("guest_profile", "douyin_id")).isTrue();
        assertThat(columnExists("guest_profile", "douyin_nickname")).isFalse();
        assertThat(columnExists("guest_profile", "douyin_profile_url")).isFalse();

        assertThat(dataTypeOf("age")).isEqualTo("INTEGER");
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM profile_field_definition
                        WHERE field_code IN ('birth_date', 'douyin_nickname', 'douyin_profile_url')
                        """)
                .query(Integer.class).single()).isZero();
    }

    /** 年龄的区间兜底在库里。应用层也拦，但库是最后一道，绕过接口写进来的也得挡住。 */
    @Test
    void theDatabaseRefusesImpossibleAges() throws Exception {
        try (Connection owner = ownerConnection()) {
            long accountId = insertAccount(owner, "13800138777");
            long profileId = insertProfile(owner, accountId);

            // 草稿允许还没填年龄。
            execute(owner, "UPDATE guest_profile SET age = NULL WHERE id = " + profileId);
            execute(owner, "UPDATE guest_profile SET age = 18 WHERE id = " + profileId);
            execute(owner, "UPDATE guest_profile SET age = 100 WHERE id = " + profileId);

            assertSqlRejected(
                    () -> execute(owner, "UPDATE guest_profile SET age = 17 WHERE id = " + profileId),
                    "ck_guest_profile_age");
            assertSqlRejected(
                    () -> execute(owner, "UPDATE guest_profile SET age = 101 WHERE id = " + profileId),
                    "ck_guest_profile_age");
        }
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

    /**
     * 订单要能对账：渠道侧订单号从下单起就有位置存，而且两笔订单不能声称是同一笔渠道单。
     *
     * <p>它与 {@code transaction_id} 是两列而不是一列：后者是「这笔钱」的流水号，
     * 结算才有；前者是「这笔单子」在渠道那边的编号，下单就有。合成一列会让
     * {@code ck_payment_order_paid_shape} 依赖的「transaction_id 非空 ⇒ 已付」变成谎话。</p>
     */
    @Test
    void paymentOrderCanBeReconciledAgainstTheChannel() throws SQLException {
        assertThat(columnExists("payment_order", "channel_trade_no")).isTrue();
        assertThat(columnExists("payment_order", "expires_at")).isTrue();

        try (Connection owner = ownerConnection()) {
            long accountId = insertAccount(owner, "13800138201");
            execute(owner, """
                    INSERT INTO payment_order (out_trade_no, user_account_id, channel, amount_minor,
                                               status, channel_trade_no)
                    VALUES ('OTN-RECON-1', %d, 'XPAY_ALIPAY', 100, 'CREATED', '20260823225910918724')
                    """.formatted(accountId));

            assertSqlRejected(
                    () -> execute(owner, """
                            INSERT INTO payment_order (out_trade_no, user_account_id, channel,
                                                       amount_minor, status, channel_trade_no)
                            VALUES ('OTN-RECON-2', %d, 'XPAY_ALIPAY', 100, 'CREATED',
                                    '20260823225910918724')
                            """.formatted(accountId)),
                    "uq_payment_order_channel_trade_no");

            // 未支付的订单可以有渠道单号而没有流水号——这正是当初缺的那个抓手。
            assertThat(queryBoolean(owner, """
                    SELECT channel_trade_no IS NOT NULL AND transaction_id IS NULL
                      FROM payment_order WHERE out_trade_no = 'OTN-RECON-1'
                    """)).isTrue();
        }
    }

    /** CLOSED 一直在 CHECK 里，但直到订单过期关单才真的被写进去。 */
    @Test
    void paymentOrderStatusAllowsClosed() throws SQLException {
        try (Connection owner = ownerConnection()) {
            long accountId = insertAccount(owner, "13800138202");
            execute(owner, """
                    INSERT INTO payment_order (out_trade_no, user_account_id, channel, amount_minor,
                                               status, expires_at)
                    VALUES ('OTN-CLOSED-1', %d, 'XPAY_ALIPAY', 100, 'CLOSED',
                            CURRENT_TIMESTAMP - INTERVAL '1 minute')
                    """.formatted(accountId));

            assertSqlRejected(
                    () -> execute(owner, """
                            UPDATE payment_order SET status = 'EXPIRED'
                             WHERE out_trade_no = 'OTN-CLOSED-1'
                            """),
                    "ck_payment_order_status");
        }
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

    private String dataTypeOf(String fieldCode) {
        return jdbc.sql("SELECT data_type FROM profile_field_definition WHERE field_code = :code")
                .param("code", fieldCode)
                .query(String.class)
                .single();
    }

    /** options_json 是 JSON 数组文本，这里只做包含判断，不引入 JSON 解析依赖。 */
    private String optionsOf(String fieldCode) {
        return jdbc.sql("SELECT options_json FROM profile_field_definition WHERE field_code = :code")
                .param("code", fieldCode)
                .query(String.class)
                .single();
    }

    private boolean tableExists(String table) {        return jdbc.sql("SELECT to_regclass('public.' || :table) IS NOT NULL")
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
