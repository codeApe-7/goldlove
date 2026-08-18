package com.love.archive.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.love.archive.testsupport.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class WechatPaymentMembershipMigrationTest extends PostgresIntegrationTest {

    @Test
    void addsOnlinePaymentTablesMembershipColumnsAndRuntimeGrants() throws SQLException {
        String databaseName = "wechat_pay_v9_" + UUID.randomUUID().toString().replace("-", "");
        createDatabase(databaseName);
        try {
            String databaseUrl = databaseUrl(databaseName);
            flyway(databaseUrl).migrate();

            try (Connection owner = ownerConnection(databaseUrl)) {
                assertThat(queryLong(owner, """
                        SELECT count(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('wechat_payment_order', 'registration_token')
                        """)).isEqualTo(2);

                assertThat(columnExists(owner, "user_account", "membership_tier")).isTrue();
                assertThat(columnExists(owner, "user_account", "membership_credit_minor")).isTrue();
                assertThat(columnExists(owner, "user_account", "registration_channel")).isTrue();
                assertThat(columnExists(owner, "payment_record", "payment_channel")).isTrue();
                assertThat(columnExists(owner, "payment_record", "out_trade_no")).isTrue();
                assertThat(columnExists(owner, "payment_record", "paid_amount_minor")).isTrue();
                assertThat(columnExists(owner, "payment_record", "membership_credit_minor")).isTrue();
                assertThat(columnExists(owner, "payment_record", "registered")).isTrue();

                // 交易号与 openid 不允许明文列。
                assertThat(columnExists(owner, "payment_record", "transaction_id")).isFalse();
                assertThat(columnExists(owner, "payment_record", "transaction_id_ciphertext")).isTrue();
                assertThat(columnExists(owner, "wechat_payment_order", "openid")).isFalse();
                assertThat(columnExists(owner, "wechat_payment_order", "openid_ciphertext")).isTrue();
                assertThat(columnExists(owner, "registration_token", "token")).isFalse();
                assertThat(columnExists(owner, "registration_token", "token_hmac")).isTrue();

                long documentId = seedAuthorizationDocument(owner);
                long adminId = seedAdmin(owner);

                // 新账号默认 VIP、零额度、手动渠道。
                long manualAccountId = seedManualAccount(owner, adminId, "manual-hmac-1");
                assertThat(queryString(owner,
                        "SELECT membership_tier FROM user_account WHERE id = " + manualAccountId))
                        .isEqualTo("VIP");
                assertThat(queryLong(owner,
                        "SELECT membership_credit_minor FROM user_account WHERE id = " + manualAccountId))
                        .isZero();
                assertThat(queryString(owner,
                        "SELECT registration_channel FROM user_account WHERE id = " + manualAccountId))
                        .isEqualTo("ADMIN_MANUAL");

                // 手动渠道仍然必须带登记管理员与账号。
                assertThatThrownBy(() -> execute(owner, """
                        INSERT INTO user_account
                            (phone_ciphertext, phone_hmac, password_hash, status, registration_channel)
                        VALUES (decode('00', 'hex'), 'manual-no-admin', 'hash', 'ACTIVE', 'ADMIN_MANUAL')
                        """))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("ck_user_account_manual_creator");

                // 线上渠道允许没有登记管理员。
                long onlineAccountId = seedOnlineAccount(owner, "online-hmac-1");
                assertThat(queryString(owner,
                        "SELECT registration_channel FROM user_account WHERE id = " + onlineAccountId))
                        .isEqualTo("WECHAT_ONLINE");

                assertThatThrownBy(() -> execute(owner,
                        "UPDATE user_account SET membership_tier = 'GOLD' WHERE id = " + onlineAccountId))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("ck_user_account_membership_tier");

                // 手动付款记录不允许携带线上字段。
                assertThatThrownBy(() -> execute(owner, """
                        INSERT INTO payment_record
                            (user_account_id, payment_reference, amount_minor, paid_at,
                             operator_admin_id, payment_channel, out_trade_no)
                        VALUES (%d, 'MANUAL-WITH-ONLINE', 100, CURRENT_TIMESTAMP, %d, 'MANUAL', 'OTN-1')
                        """.formatted(manualAccountId, adminId)))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("ck_payment_record_manual_shape");

                // 线上付款记录允许账号为空，但必须自带商户订单号与交易号密文。
                assertThatThrownBy(() -> execute(owner, """
                        INSERT INTO payment_record
                            (payment_reference, amount_minor, paid_at, payment_channel, out_trade_no,
                             paid_amount_minor)
                        VALUES ('OTN-MISSING-TX', 100, CURRENT_TIMESTAMP, 'WECHAT_JSAPI', 'OTN-MISSING-TX', 100)
                        """))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("ck_payment_record_online_shape");

                long onlinePaymentId = seedOnlinePaymentRecord(owner, "OTN-OK-1", documentId);
                assertThat(queryLong(owner,
                        "SELECT membership_credit_minor FROM payment_record WHERE id = " + onlinePaymentId))
                        .isZero();
                assertThat(queryBoolean(owner,
                        "SELECT registered FROM payment_record WHERE id = " + onlinePaymentId)).isFalse();

                // 未绑定账号的付款记录不能标记为已注册。
                assertThatThrownBy(() -> execute(owner,
                        "UPDATE payment_record SET registered = TRUE WHERE id = " + onlinePaymentId))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("ck_payment_record_registered_account");

                // 计入额度不得超过付款金额。
                assertThatThrownBy(() -> execute(owner,
                        "UPDATE payment_record SET membership_credit_minor = 999 WHERE id = " + onlinePaymentId))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("ck_payment_record_membership_credit");

                long orderId = seedOrder(owner, "OTN-ORDER-1", documentId);
                assertThat(queryString(owner,
                        "SELECT status FROM wechat_payment_order WHERE id = " + orderId)).isEqualTo("CREATED");

                // 已支付订单必须同时具备交易号、支付时间与付款记录。
                assertThatThrownBy(() -> execute(owner,
                        "UPDATE wechat_payment_order SET status = 'PAID' WHERE id = " + orderId))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("ck_wechat_payment_order_settlement");

                execute(owner, """
                        INSERT INTO registration_token
                            (token_hmac, wechat_payment_order_id, out_trade_no,
                             openid_ciphertext, openid_hmac, expires_at)
                        VALUES ('token-hmac-1', %d, 'OTN-ORDER-1', decode('00', 'hex'), 'openid-hmac-1',
                                CURRENT_TIMESTAMP + INTERVAL '30 minutes')
                        """.formatted(orderId));

                // 同一订单只能有一个待用令牌。
                assertThatThrownBy(() -> execute(owner, """
                        INSERT INTO registration_token
                            (token_hmac, wechat_payment_order_id, out_trade_no,
                             openid_ciphertext, openid_hmac, expires_at)
                        VALUES ('token-hmac-2', %d, 'OTN-ORDER-1', decode('00', 'hex'), 'openid-hmac-1',
                                CURRENT_TIMESTAMP + INTERVAL '30 minutes')
                        """.formatted(orderId)))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("uq_registration_token_unused_order");

                // 已使用令牌必须记录使用时间与账号。
                assertThatThrownBy(() -> execute(owner,
                        "UPDATE registration_token SET status = 'USED' WHERE token_hmac = 'token-hmac-1'"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("ck_registration_token_consumption");
            }

            try (Connection runtime = runtimeConnection(databaseUrl)) {
                assertThat(queryBoolean(runtime, """
                        SELECT has_table_privilege('archive_app', 'wechat_payment_order', 'INSERT')
                           AND has_table_privilege('archive_app', 'wechat_payment_order', 'UPDATE')
                           AND has_table_privilege('archive_app', 'wechat_payment_order', 'SELECT')
                           AND has_table_privilege('archive_app', 'registration_token', 'INSERT')
                           AND has_table_privilege('archive_app', 'registration_token', 'UPDATE')
                           AND has_table_privilege('archive_app', 'registration_token', 'SELECT')
                        """)).isTrue();
                assertThat(queryBoolean(runtime, """
                        SELECT has_table_privilege('archive_app', 'wechat_payment_order', 'DELETE')
                            OR has_table_privilege('archive_app', 'registration_token', 'DELETE')
                        """)).isFalse();
                assertThatThrownBy(() -> execute(runtime,
                        "ALTER TABLE registration_token ADD COLUMN sneaky TEXT"))
                        .isInstanceOf(SQLException.class);
            }
        } finally {
            dropDatabase(databaseName);
        }
    }

    @Test
    void backfillsMembershipCreditForExistingManualPayments() throws SQLException {
        String databaseName = "wechat_pay_v8_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        createDatabase(databaseName);
        try {
            String databaseUrl = databaseUrl(databaseName);
            Flyway.configure()
                    .dataSource(databaseUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration")
                    .target("8")
                    .load()
                    .migrate();

            long accountId;
            try (Connection owner = ownerConnection(databaseUrl)) {
                long adminId = seedAdmin(owner);
                accountId = seedManualAccount(owner, adminId, "legacy-hmac-1");
                execute(owner, """
                        INSERT INTO payment_record
                            (user_account_id, payment_reference, amount_minor, paid_at, operator_admin_id)
                        VALUES (%d, 'LEGACY-PAY-1', 19900, CURRENT_TIMESTAMP, %d),
                               (%d, 'LEGACY-PAY-2', 40000, CURRENT_TIMESTAMP, %d)
                        """.formatted(accountId, adminId, accountId, adminId));
            }

            assertThat(flyway(databaseUrl).migrate().migrationsExecuted).isEqualTo(1);

            try (Connection owner = ownerConnection(databaseUrl)) {
                assertThat(queryLong(owner,
                        "SELECT membership_credit_minor FROM user_account WHERE id = " + accountId))
                        .isEqualTo(59_900L);
                assertThat(queryLong(owner, """
                        SELECT count(*) FROM payment_record
                        WHERE payment_channel = 'MANUAL' AND membership_credit_minor = amount_minor
                        """)).isEqualTo(2);
                // 阈值属于应用配置，迁移不推断等级。
                assertThat(queryString(owner,
                        "SELECT membership_tier FROM user_account WHERE id = " + accountId)).isEqualTo("VIP");
            }
        } finally {
            dropDatabase(databaseName);
        }
    }

    private static long seedAuthorizationDocument(Connection owner) throws SQLException {
        return queryLong(owner, """
                SELECT id FROM authorization_document
                WHERE document_code = 'PAID_PROFILE_LIVE_CONTENT' AND status = 'ACTIVE'
                """);
    }

    private static long seedAdmin(Connection owner) throws SQLException {
        return queryLong(owner, """
                INSERT INTO admin_user (username, display_name, password_hash, status)
                VALUES ('v9-operator', 'V9 Operator', 'hash', 'ACTIVE')
                RETURNING id
                """);
    }

    private static long seedManualAccount(Connection owner, long adminId, String phoneHmac)
            throws SQLException {
        return queryLong(owner, """
                INSERT INTO user_account
                    (phone_ciphertext, phone_hmac, password_hash, status, created_by_admin_id)
                VALUES (decode('00', 'hex'), '%s', 'hash', 'ACTIVE', %d)
                RETURNING id
                """.formatted(phoneHmac, adminId));
    }

    private static long seedOnlineAccount(Connection owner, String phoneHmac) throws SQLException {
        return queryLong(owner, """
                INSERT INTO user_account
                    (phone_ciphertext, phone_hmac, password_hash, status, registration_channel)
                VALUES (decode('00', 'hex'), '%s', 'hash', 'ACTIVE', 'WECHAT_ONLINE')
                RETURNING id
                """.formatted(phoneHmac));
    }

    private static long seedOnlinePaymentRecord(Connection owner, String outTradeNo, long documentId)
            throws SQLException {
        return queryLong(owner, """
                INSERT INTO payment_record
                    (payment_reference, amount_minor, paid_at, payment_channel, out_trade_no,
                     transaction_id_ciphertext, transaction_id_hmac, paid_amount_minor,
                     presented_authorization_document_id)
                VALUES ('%s', 100, CURRENT_TIMESTAMP, 'WECHAT_JSAPI', '%s',
                        decode('00', 'hex'), 'tx-hmac-%s', 100, %d)
                RETURNING id
                """.formatted(outTradeNo, outTradeNo, outTradeNo, documentId));
    }

    private static long seedOrder(Connection owner, String outTradeNo, long documentId) throws SQLException {
        return queryLong(owner, """
                INSERT INTO wechat_payment_order
                    (out_trade_no, openid_ciphertext, openid_hmac, description, amount_minor,
                     presented_authorization_document_id)
                VALUES ('%s', decode('00', 'hex'), 'openid-hmac-1', '建档服务', 100, %d)
                RETURNING id
                """.formatted(outTradeNo, documentId));
    }

    private static Flyway flyway(String databaseUrl) {
        return Flyway.configure()
                .dataSource(databaseUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
    }

    private static void createDatabase(String databaseName) throws SQLException {
        try (Connection owner = ownerConnection(POSTGRES.getJdbcUrl())) {
            execute(owner, "CREATE DATABASE " + databaseName);
        }
    }

    private static void dropDatabase(String databaseName) throws SQLException {
        try (Connection owner = ownerConnection(POSTGRES.getJdbcUrl())) {
            execute(owner, "DROP DATABASE " + databaseName + " WITH (FORCE)");
        }
    }

    private static String databaseUrl(String databaseName) {
        return "jdbc:postgresql://%s:%d/%s"
                .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), databaseName);
    }

    private static Connection ownerConnection(String databaseUrl) throws SQLException {
        return DriverManager.getConnection(databaseUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection runtimeConnection(String databaseUrl) throws SQLException {
        return DriverManager.getConnection(databaseUrl, "archive_app", "integration-runtime-only");
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                )
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static int execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static boolean queryBoolean(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }
}
