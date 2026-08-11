ALTER TABLE profile_photo
    DROP COLUMN sha256,
    DROP COLUMN size_bytes,
    DROP COLUMN content_type,
    DROP COLUMN width,
    DROP COLUMN height;

ALTER TABLE profile_revision_photo
    DROP COLUMN sha256,
    DROP COLUMN size_bytes,
    DROP COLUMN content_type,
    DROP COLUMN width,
    DROP COLUMN height;
