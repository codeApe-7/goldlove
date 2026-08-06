GRANT USAGE ON SCHEMA public TO archive_app;

GRANT SELECT, INSERT, UPDATE
    ON admin_user, user_account, external_identity, payment_record, activation_credential
    TO archive_app;

GRANT SELECT, INSERT ON audit_log TO archive_app;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO archive_app;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM archive_app;
REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM archive_app;
