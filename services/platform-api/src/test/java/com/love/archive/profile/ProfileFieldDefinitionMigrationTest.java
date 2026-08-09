package com.love.archive.profile;

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

class ProfileFieldDefinitionMigrationTest extends PostgresIntegrationTest {

    @Test
    void upgradesAnExistingV3DatabaseWithoutRewritingItsMigration() throws SQLException {
        String databaseName = "profile_v3_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        createDatabase(databaseName);
        try {
            String databaseUrl = databaseUrl(databaseName);
            Flyway v3Flyway = flyway(databaseUrl, "3");
            v3Flyway.migrate();

            Integer v3ChecksumBefore;
            try (Connection owner = ownerConnection(databaseUrl)) {
                v3ChecksumBefore = queryInteger(owner, """
                        SELECT checksum
                        FROM flyway_schema_history
                        WHERE version = '3' AND success
                        """);
                assertThat(v3ChecksumBefore).isNotNull();
                assertThat(columnExists(owner, "profile_field_definition", "ever_used")).isFalse();

                execute(owner, """
                        INSERT INTO profile_field_definition
                            (field_code, label, storage_kind, data_type, required, enabled, sort_order)
                        VALUES ('legacy-dynamic', 'Legacy dynamic', 'DYNAMIC', 'TEXT', FALSE, TRUE, 800)
                        """);
            }

            Flyway latestFlyway = flyway(databaseUrl, null);
            assertThat(latestFlyway.migrate().migrationsExecuted).isEqualTo(2);

            try (Connection owner = ownerConnection(databaseUrl)) {
                assertThat(queryInteger(owner, """
                        SELECT checksum
                        FROM flyway_schema_history
                        WHERE version = '3' AND success
                        """)).isEqualTo(v3ChecksumBefore);
                assertThat(queryLong(owner, """
                        SELECT count(*)
                        FROM flyway_schema_history
                        WHERE version = '4' AND success
                        """)).isEqualTo(1);
                assertThat(queryLong(owner, """
                        SELECT count(*)
                        FROM flyway_schema_history
                        WHERE version = '5' AND success
                        """)).isEqualTo(1);
                assertThat(queryLong(owner, """
                        SELECT count(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('profile_photo', 'profile_revision_photo')
                        """)).isEqualTo(2);
                assertThat(queryLong(owner, """
                        SELECT count(*)
                        FROM pg_trigger
                        WHERE tgname = 'profile_revision_photo_immutable' AND NOT tgisinternal
                        """)).isEqualTo(1);
                assertThat(columnExists(owner, "profile_field_definition", "ever_used")).isTrue();
                assertThat(queryBoolean(owner, """
                        SELECT ever_used
                        FROM profile_field_definition
                        WHERE field_code = 'legacy-dynamic'
                        """)).isTrue();
                assertThat(queryLong(owner, """
                        SELECT count(*)
                        FROM profile_field_definition
                        WHERE storage_kind = 'CORE' AND ever_used
                        """)).isZero();

                execute(owner, """
                        INSERT INTO profile_field_definition
                            (field_code, label, storage_kind, data_type, required, enabled, sort_order)
                        VALUES ('new-dynamic', 'New dynamic', 'DYNAMIC', 'TEXT', FALSE, TRUE, 900)
                        """);
                assertThat(queryBoolean(owner, """
                        SELECT ever_used
                        FROM profile_field_definition
                        WHERE field_code = 'new-dynamic'
                        """)).isFalse();
                assertThat(queryLong(owner, """
                        SELECT count(*)
                        FROM pg_trigger
                        WHERE tgname IN (
                            'profile_field_definition_identity_immutable',
                            'profile_field_value_marks_definition_used'
                        ) AND NOT tgisinternal
                        """)).isEqualTo(2);

                assertThatThrownBy(() -> execute(owner, """
                        UPDATE profile_field_definition
                        SET field_code = 'legacy-renamed'
                        WHERE field_code = 'legacy-dynamic'
                        """))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("used profile field definition identity is immutable");
                assertThatThrownBy(() -> execute(owner, """
                        UPDATE profile_field_definition
                        SET storage_kind = 'CORE'
                        WHERE field_code = 'new-dynamic'
                        """))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("profile field definition storage kind is immutable");
            }
        } finally {
            dropDatabase(databaseName);
        }
    }

    private static Flyway flyway(String databaseUrl, String target) {
        var configuration = Flyway.configure()
                .dataSource(databaseUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
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

    private static Integer queryInteger(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return (Integer) resultSet.getObject(1);
        }
    }

    private static boolean queryBoolean(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }
}
