-- Run after 001 and 002, before starting Stage 2. No scheduling records are changed.
BEGIN;
LOCK TABLE weekly_schedule IN ACCESS EXCLUSIVE MODE;
DO $$
DECLARE status_column smallint; existing record;
BEGIN
    SELECT attnum INTO STRICT status_column FROM pg_attribute
    WHERE attrelid = 'weekly_schedule'::regclass AND attname = 'status' AND NOT attisdropped;
    FOR existing IN SELECT conname FROM pg_constraint
        WHERE conrelid = 'weekly_schedule'::regclass AND contype = 'c'
          AND conkey = ARRAY[status_column]::smallint[]
    LOOP
        IF existing.conname NOT IN ('ck_weekly_schedule_draft', 'weekly_schedule_status_check', 'ck_weekly_schedule_status') THEN
            RAISE EXCEPTION 'Unexpected status constraint %, review before migrating', existing.conname;
        END IF;
        EXECUTE format('ALTER TABLE weekly_schedule DROP CONSTRAINT %I', existing.conname);
    END LOOP;
END $$;
ALTER TABLE weekly_schedule ADD CONSTRAINT ck_weekly_schedule_status CHECK (status IN ('DRAFT', 'PUBLISHED'));
COMMIT;
