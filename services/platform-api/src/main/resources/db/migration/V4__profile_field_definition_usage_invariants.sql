ALTER TABLE public.profile_field_definition
    ADD COLUMN ever_used BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing dynamic values may have been deleted before this monotonic marker existed.
-- Freeze every pre-V4 dynamic definition because absence of a current row cannot prove non-use.
UPDATE public.profile_field_definition
SET ever_used = TRUE
WHERE storage_kind = 'DYNAMIC';

CREATE FUNCTION public.preserve_profile_field_definition_identity() RETURNS trigger AS $$
BEGIN
    IF OLD.storage_kind IS DISTINCT FROM NEW.storage_kind THEN
        RAISE EXCEPTION 'profile field definition storage kind is immutable';
    END IF;
    IF OLD.ever_used AND NOT NEW.ever_used THEN
        RAISE EXCEPTION 'profile field definition usage cannot be reset';
    END IF;
    IF OLD.ever_used AND (
        OLD.field_code IS DISTINCT FROM NEW.field_code
        OR OLD.data_type IS DISTINCT FROM NEW.data_type
    ) THEN
        RAISE EXCEPTION 'used profile field definition identity is immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql
SET search_path = pg_catalog;

CREATE FUNCTION public.mark_profile_field_definition_ever_used() RETURNS trigger AS $$
BEGIN
    UPDATE public.profile_field_definition
    SET ever_used = TRUE
    WHERE id = NEW.field_definition_id AND NOT ever_used;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql
SET search_path = pg_catalog;

CREATE TRIGGER profile_field_definition_identity_immutable
    BEFORE UPDATE ON public.profile_field_definition
    FOR EACH ROW EXECUTE FUNCTION public.preserve_profile_field_definition_identity();

CREATE TRIGGER profile_field_value_marks_definition_used
    BEFORE INSERT ON public.profile_field_value
    FOR EACH ROW EXECUTE FUNCTION public.mark_profile_field_definition_ever_used();
