CREATE ROLE archive_app LOGIN PASSWORD 'integration-runtime-only';
GRANT CONNECT ON DATABASE marriage_archive TO archive_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO archive_app;
