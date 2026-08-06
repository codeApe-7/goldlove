package com.love.archive.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.love.archive.testsupport.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProfileConsentPersistenceTest extends PostgresIntegrationTest {

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
}
