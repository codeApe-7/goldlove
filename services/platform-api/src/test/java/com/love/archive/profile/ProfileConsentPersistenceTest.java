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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProfileConsentPersistenceTest extends PostgresIntegrationTest {

    private static final String PAYLOAD_SHA256 = "a".repeat(64);

    @Autowired
    private JdbcClient jdbc;

    @Test
    void seedsAuthorizationAndCoreFieldsAndEnforcesOnePendingRevision() {
        assertThat(jdbc.sql("select count(*) from authorization_document where status='ACTIVE'")
                .query(Integer.class)
                .single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from profile_field_definition where storage_kind='CORE'")
                .query(Integer.class)
                .single()).isEqualTo(7);

        long adminId = jdbc.sql("""
                        insert into admin_user (username, display_name, password_hash, status)
                        values ('profile-consent-persistence-admin', 'Profile Consent Test', 'test-hash', 'ACTIVE')
                        returning id
                        """)
                .query(Long.class)
                .single();
        long accountId = jdbc.sql("""
                        insert into user_account (phone_ciphertext, phone_hmac, status, created_by_admin_id)
                        values (decode('010203', 'hex'), 'profile-consent-persistence-phone',
                                'PAID_PENDING_ACTIVATION', :adminId)
                        returning id
                        """)
                .param("adminId", adminId)
                .query(Long.class)
                .single();
        long profileId = jdbc.sql("""
                        insert into guest_profile (profile_no, user_account_id, status)
                        values (:profileNo, :accountId, 'DRAFT')
                        returning id
                        """)
                .param("profileNo", UUID.randomUUID())
                .param("accountId", accountId)
                .query(Long.class)
                .single();

        jdbc.sql("""
                        insert into profile_revision
                            (guest_profile_id, revision_number, status, submitted_by_account_id,
                             submitted_at, review_deadline_at, submission_key_hmac, request_payload_sha256)
                        values (:profileId, 1, 'PENDING', :accountId,
                                now(), now() + interval '24 hours', :submissionKey, :payloadSha256)
                        """)
                .param("profileId", profileId)
                .param("accountId", accountId)
                .param("submissionKey", "key-one")
                .param("payloadSha256", "a".repeat(64))
                .update();

        assertThatThrownBy(() -> jdbc.sql("""
                        insert into profile_revision
                            (guest_profile_id, revision_number, status, submitted_by_account_id,
                             submitted_at, review_deadline_at, submission_key_hmac, request_payload_sha256)
                        values (:profileId, 2, 'PENDING', :accountId,
                                now(), now() + interval '24 hours', :submissionKey, :payloadSha256)
                        """)
                .param("profileId", profileId)
                .param("accountId", accountId)
                .param("submissionKey", "key-two")
                .param("payloadSha256", "b".repeat(64))
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_profile_revision_pending");
    }

    @Test
    void ownerConnectionEnforcesProfileConsentConstraintsAndImmutability() throws SQLException {
        try (Connection owner = ownerConnection()) {
            OwnerFixture fixture = createOwnerFixture(owner);
            long wechatOnlyAccountId = insertOwnerAccount(owner, uniqueSuffix());
            long douyinOnlyAccountId = insertOwnerAccount(owner, uniqueSuffix());

            assertSqlRejected(
                    () -> execute(owner, """
                            INSERT INTO guest_profile (profile_no, user_account_id, status, wechat_id_ciphertext)
                            VALUES (gen_random_uuid(), %d, 'DRAFT', decode('01', 'hex'))
                            """.formatted(wechatOnlyAccountId)),
                    "ck_guest_profile_wechat_pair");
            assertSqlRejected(
                    () -> execute(owner, """
                            INSERT INTO guest_profile (profile_no, user_account_id, status, douyin_id_ciphertext)
                            VALUES (gen_random_uuid(), %d, 'DRAFT', decode('01', 'hex'))
                            """.formatted(douyinOnlyAccountId)),
                    "ck_guest_profile_douyin_pair");
            assertSqlRejected(
                    () -> execute(owner, revisionInsert(
                            fixture.profileId(), 2, "APPROVED", fixture.accountId(),
                            "owner-pair-wechat-" + uniqueSuffix(), "wechat_id_ciphertext")),
                    "ck_profile_revision_wechat_pair");
            assertSqlRejected(
                    () -> execute(owner, revisionInsert(
                            fixture.profileId(), 3, "APPROVED", fixture.accountId(),
                            "owner-pair-douyin-" + uniqueSuffix(), "douyin_id_ciphertext")),
                    "ck_profile_revision_douyin_pair");
            assertSqlRejected(
                    () -> execute(owner, """
                            INSERT INTO profile_field_value
                                (guest_profile_id, field_definition_id, text_value, option_value)
                            VALUES (%d, %d, 'two values', 'also set')
                            """.formatted(fixture.profileId(), fixture.fieldDefinitionId())),
                    "ck_profile_field_value_exactly_one_value");
            assertSqlRejected(
                    () -> execute(owner, """
                            INSERT INTO profile_revision_field_value
                                (profile_revision_id, field_code, field_label, data_type, text_value, option_value)
                            VALUES (%d, 'owner-two-values', 'Owner two values', 'TEXT', 'first', 'second')
                            """.formatted(fixture.revisionId())),
                    "ck_profile_revision_field_value_exactly_one_value");

            long rejectedReviewRevisionId = insertRevision(
                    owner, fixture.profileId(), fixture.accountId(), 4, "REJECTED", "owner-rejected-" + uniqueSuffix());
            assertSqlRejected(
                    () -> execute(owner, """
                            INSERT INTO profile_review_record
                                (profile_revision_id, reviewer_admin_id, result, comment, reviewed_at, request_id)
                            VALUES (%d, %d, 'REJECTED', '   ', now(), 'owner-empty-comment')
                            """.formatted(rejectedReviewRevisionId, fixture.adminId())),
                    "ck_profile_review_record_rejected_comment");

            assertSqlRejected(
                    () -> execute(owner, "UPDATE authorization_record SET source_page = 'changed' WHERE id = %d"
                            .formatted(fixture.authorizationRecordId())),
                    "authorization_record rows are immutable");
            assertSqlRejected(
                    () -> execute(owner, "UPDATE profile_revision_field_value SET text_value = 'changed' WHERE id = %d"
                            .formatted(fixture.revisionFieldValueId())),
                    "profile_revision_field_value rows are immutable");
            assertSqlRejected(
                    () -> execute(owner, "DELETE FROM profile_review_record WHERE id = %d"
                            .formatted(fixture.reviewRecordId())),
                    "profile_review_record rows are immutable");

            assertThat(execute(owner, """
                    UPDATE profile_revision
                    SET status = 'APPROVED', reviewed_at = now(), version = version + 1
                    WHERE id = %d
                    """.formatted(fixture.revisionId()))).isEqualTo(1);
            assertSqlRejected(
                    () -> execute(owner, "UPDATE profile_revision SET city = 'changed' WHERE id = %d"
                            .formatted(fixture.revisionId())),
                    "profile_revision only permits status, reviewed_at, and version updates");
            assertSqlRejected(
                    () -> execute(owner, "DELETE FROM profile_revision WHERE id = %d".formatted(fixture.revisionId())),
                    "profile_revision rows are immutable");
        }
    }

    @Test
    void runtimeRoleAllowsOnlyDeclaredProfileConsentOperations() throws SQLException {
        try (Connection owner = ownerConnection(); Connection runtime = runtimeConnection()) {
            OwnerFixture fixture = createOwnerFixture(owner);
            String suffix = uniqueSuffix();
            long runtimeAccountId = insertOwnerAccount(owner, suffix);

            assertThat(queryLong(runtime, "SELECT count(*) FROM authorization_document")).isPositive();
            assertSqlRejected(
                    () -> execute(runtime, """
                            INSERT INTO authorization_document
                                (document_code, version, title, content, content_sha256, status, effective_at)
                            VALUES ('runtime-document-%s', 'v1', 'Runtime document', 'content', '%s', 'DRAFT', now())
                            """.formatted(suffix, PAYLOAD_SHA256)),
                    "permission denied");

            assertThat(insertReturningId(runtime, """
                    INSERT INTO authorization_record
                        (user_account_id, authorization_document_id, accepted, accepted_at, effective_at,
                         expires_at, source_page, client_ip_hmac, user_agent_sha256, session_reference_hmac)
                    VALUES (%d, %d, TRUE, now(), now(), now() + interval '1 year',
                            'runtime-test', 'runtime-ip', '%s', 'runtime-session')
                    RETURNING id
                    """.formatted(runtimeAccountId, fixture.authorizationDocumentId(), PAYLOAD_SHA256))).isPositive();
            assertThat(queryLong(runtime, "SELECT count(*) FROM authorization_record")).isPositive();
            assertSqlRejected(
                    () -> execute(runtime, "UPDATE authorization_record SET source_page = 'changed' WHERE id = %d"
                            .formatted(fixture.authorizationRecordId())),
                    "permission denied");
            assertSqlRejected(
                    () -> execute(runtime, "DELETE FROM authorization_record WHERE id = %d"
                            .formatted(fixture.authorizationRecordId())),
                    "permission denied");

            long profileId = insertReturningId(runtime, """
                    INSERT INTO guest_profile (profile_no, user_account_id, status)
                    VALUES (gen_random_uuid(), %d, 'DRAFT')
                    RETURNING id
                    """.formatted(runtimeAccountId));
            assertThat(execute(runtime, "UPDATE guest_profile SET city = 'Runtime City' WHERE id = %d".formatted(profileId)))
                    .isEqualTo(1);
            assertThat(queryLong(runtime, "SELECT count(*) FROM guest_profile")).isPositive();
            assertSqlRejected(
                    () -> execute(runtime, "DELETE FROM guest_profile WHERE id = %d".formatted(profileId)),
                    "permission denied");

            long fieldDefinitionId = insertReturningId(runtime, """
                    INSERT INTO profile_field_definition
                        (field_code, label, storage_kind, data_type, required, enabled, sort_order)
                    VALUES ('runtime-field-%s', 'Runtime field', 'DYNAMIC', 'TEXT', FALSE, TRUE, 900)
                    RETURNING id
                    """.formatted(suffix));
            assertThat(execute(runtime, "UPDATE profile_field_definition SET label = 'Updated field' WHERE id = %d"
                    .formatted(fieldDefinitionId))).isEqualTo(1);
            assertThat(queryLong(runtime, "SELECT count(*) FROM profile_field_definition")).isPositive();

            long fieldValueId = insertReturningId(runtime, """
                    INSERT INTO profile_field_value (guest_profile_id, field_definition_id, text_value)
                    VALUES (%d, %d, 'runtime value')
                    RETURNING id
                    """.formatted(profileId, fieldDefinitionId));
            assertThat(execute(runtime, "UPDATE profile_field_value SET text_value = 'updated value' WHERE id = %d"
                    .formatted(fieldValueId))).isEqualTo(1);
            assertThat(queryLong(runtime, "SELECT count(*) FROM profile_field_value")).isPositive();
            assertThat(execute(runtime, "DELETE FROM profile_field_value WHERE id = %d".formatted(fieldValueId)))
                    .isEqualTo(1);
            assertSqlRejected(
                    () -> execute(runtime, "DELETE FROM profile_field_definition WHERE id = %d".formatted(fieldDefinitionId)),
                    "permission denied");

            long revisionId = insertRevision(runtime, profileId, runtimeAccountId, 1, "PENDING", "runtime-revision-" + suffix);
            assertThat(execute(runtime, """
                    UPDATE profile_revision
                    SET status = 'APPROVED', reviewed_at = now(), version = version + 1
                    WHERE id = %d
                    """.formatted(revisionId))).isEqualTo(1);
            assertThat(queryLong(runtime, "SELECT count(*) FROM profile_revision")).isPositive();
            assertSqlRejected(
                    () -> execute(runtime, "DELETE FROM profile_revision WHERE id = %d".formatted(revisionId)),
                    "permission denied");

            long revisionFieldValueId = insertReturningId(runtime, """
                    INSERT INTO profile_revision_field_value
                        (profile_revision_id, field_code, field_label, data_type, text_value)
                    VALUES (%d, 'runtime-snapshot-%s', 'Runtime snapshot', 'TEXT', 'snapshot')
                    RETURNING id
                    """.formatted(revisionId, suffix));
            assertThat(queryLong(runtime, "SELECT count(*) FROM profile_revision_field_value")).isPositive();
            assertSqlRejected(
                    () -> execute(runtime, "UPDATE profile_revision_field_value SET text_value = 'changed' WHERE id = %d"
                            .formatted(revisionFieldValueId)),
                    "permission denied");
            assertSqlRejected(
                    () -> execute(runtime, "DELETE FROM profile_revision_field_value WHERE id = %d"
                            .formatted(revisionFieldValueId)),
                    "permission denied");

            long reviewRecordId = insertReturningId(runtime, """
                    INSERT INTO profile_review_record
                        (profile_revision_id, reviewer_admin_id, result, reviewed_at, request_id)
                    VALUES (%d, %d, 'APPROVED', now(), 'runtime-review-%s')
                    RETURNING id
                    """.formatted(revisionId, fixture.adminId(), suffix));
            assertThat(queryLong(runtime, "SELECT count(*) FROM profile_review_record")).isPositive();
            assertSqlRejected(
                    () -> execute(runtime, "UPDATE profile_review_record SET request_id = 'changed' WHERE id = %d"
                            .formatted(reviewRecordId)),
                    "permission denied");
            assertSqlRejected(
                    () -> execute(runtime, "DELETE FROM profile_review_record WHERE id = %d"
                            .formatted(reviewRecordId)),
                    "permission denied");
            assertSqlRejected(() -> execute(runtime, "TRUNCATE profile_field_value"), "permission denied");
            assertSqlRejected(() -> execute(runtime, "CREATE TABLE runtime_forbidden (id BIGINT)"), "permission denied");
        }
    }

    private static OwnerFixture createOwnerFixture(Connection owner) throws SQLException {
        String suffix = uniqueSuffix();
        long adminId = insertOwnerAdmin(owner, suffix);
        long accountId = insertOwnerAccount(owner, suffix, adminId);
        long authorizationDocumentId = queryLong(owner,
                "SELECT id FROM authorization_document WHERE document_code = 'PAID_PROFILE_LIVE_CONTENT' AND status = 'ACTIVE'");
        long profileId = insertReturningId(owner, """
                INSERT INTO guest_profile (profile_no, user_account_id, status)
                VALUES (gen_random_uuid(), %d, 'DRAFT')
                RETURNING id
                """.formatted(accountId));
        long fieldDefinitionId = insertReturningId(owner, """
                INSERT INTO profile_field_definition
                    (field_code, label, storage_kind, data_type, required, enabled, sort_order)
                VALUES ('owner-field-%s', 'Owner field', 'DYNAMIC', 'TEXT', FALSE, TRUE, 800)
                RETURNING id
                """.formatted(suffix));
        long revisionId = insertRevision(owner, profileId, accountId, 1, "PENDING", "owner-revision-" + suffix);
        long authorizationRecordId = insertReturningId(owner, """
                INSERT INTO authorization_record
                    (user_account_id, authorization_document_id, accepted, accepted_at, effective_at,
                     expires_at, source_page, client_ip_hmac, user_agent_sha256, session_reference_hmac)
                VALUES (%d, %d, TRUE, now(), now(), now() + interval '1 year',
                        'owner-test', 'owner-ip', '%s', 'owner-session')
                RETURNING id
                """.formatted(accountId, authorizationDocumentId, PAYLOAD_SHA256));
        long revisionFieldValueId = insertReturningId(owner, """
                INSERT INTO profile_revision_field_value
                    (profile_revision_id, field_code, field_label, data_type, text_value)
                VALUES (%d, 'owner-snapshot-%s', 'Owner snapshot', 'TEXT', 'snapshot')
                RETURNING id
                """.formatted(revisionId, suffix));
        long reviewRecordId = insertReturningId(owner, """
                INSERT INTO profile_review_record
                    (profile_revision_id, reviewer_admin_id, result, reviewed_at, request_id)
                VALUES (%d, %d, 'APPROVED', now(), 'owner-review-%s')
                RETURNING id
                """.formatted(revisionId, adminId, suffix));
        return new OwnerFixture(
                adminId,
                accountId,
                authorizationDocumentId,
                profileId,
                fieldDefinitionId,
                revisionId,
                authorizationRecordId,
                revisionFieldValueId,
                reviewRecordId);
    }

    private static long insertOwnerAdmin(Connection connection, String suffix) throws SQLException {
        return insertReturningId(connection, """
                INSERT INTO admin_user (username, display_name, password_hash, status)
                VALUES ('owner-admin-%s', 'Owner admin', 'test-hash', 'ACTIVE')
                RETURNING id
                """.formatted(suffix));
    }

    private static long insertOwnerAccount(Connection connection, String suffix) throws SQLException {
        return insertOwnerAccount(connection, suffix, insertOwnerAdmin(connection, suffix));
    }

    private static long insertOwnerAccount(Connection connection, String suffix, long adminId) throws SQLException {
        return insertReturningId(connection, """
                INSERT INTO user_account (phone_ciphertext, phone_hmac, status, created_by_admin_id)
                VALUES (decode('010203', 'hex'), 'owner-phone-%s', 'PAID_PENDING_ACTIVATION', %d)
                RETURNING id
                """.formatted(suffix, adminId));
    }

    private static long insertRevision(
            Connection connection,
            long profileId,
            long accountId,
            int revisionNumber,
            String status,
            String submissionKey) throws SQLException {
        return insertReturningId(connection, revisionInsert(
                profileId, revisionNumber, status, accountId, submissionKey, null));
    }

    private static String revisionInsert(
            long profileId,
            int revisionNumber,
            String status,
            long accountId,
            String submissionKey,
            String identifierCiphertextColumn) {
        String columns = "guest_profile_id, revision_number, status, submitted_by_account_id, submitted_at, "
                + "review_deadline_at, submission_key_hmac, request_payload_sha256";
        String values = "%d, %d, '%s', %d, now(), now() + interval '24 hours', '%s', '%s'"
                .formatted(profileId, revisionNumber, status, accountId, submissionKey, PAYLOAD_SHA256);
        if (identifierCiphertextColumn != null) {
            return "INSERT INTO profile_revision (" + columns + ", " + identifierCiphertextColumn + ") VALUES ("
                    + values + ", decode('01', 'hex'))";
        }
        return "INSERT INTO profile_revision (" + columns + ") VALUES (" + values + ") RETURNING id";
    }

    private static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection runtimeConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "archive_app", "integration-runtime-only");
    }

    private static long insertReturningId(Connection connection, String sql) throws SQLException {
        return queryLong(connection, sql);
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
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

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @FunctionalInterface
    private interface SqlAction {
        void execute() throws SQLException;
    }

    private record OwnerFixture(
            long adminId,
            long accountId,
            long authorizationDocumentId,
            long profileId,
            long fieldDefinitionId,
            long revisionId,
            long authorizationRecordId,
            long revisionFieldValueId,
            long reviewRecordId) {
    }
}
