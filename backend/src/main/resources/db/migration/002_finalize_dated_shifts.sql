-- Run only after explicit legacy date/schedule backfill (see db/MIGRATION.md).
-- Also installs native constraints after Hibernate creates disposable test tables.
BEGIN;
LOCK TABLE weekly_schedule, shift IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM shift s LEFT JOIN weekly_schedule w ON w.id = s.weekly_schedule_id
        WHERE s.employee_id IS NULL OR s.start_time IS NULL OR s.end_time IS NULL
           OR s.start_time >= s.end_time OR s.shift_date IS NULL OR w.id IS NULL
           OR s.shift_date < w.week_start_date OR s.shift_date > w.week_start_date + 6
    ) THEN
        RAISE EXCEPTION 'Legacy shifts need explicit, valid date/schedule/employee/time backfill before finalization';
    END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;

ALTER TABLE shift ALTER COLUMN employee_id SET NOT NULL;
ALTER TABLE shift ALTER COLUMN weekly_schedule_id SET NOT NULL;
ALTER TABLE shift ALTER COLUMN shift_date SET NOT NULL;
ALTER TABLE shift ALTER COLUMN start_time SET NOT NULL;
ALTER TABLE shift ALTER COLUMN end_time SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'shift'::regclass AND conname = 'fk_shift_weekly_schedule') THEN
        ALTER TABLE shift ADD CONSTRAINT fk_shift_weekly_schedule
            FOREIGN KEY (weekly_schedule_id) REFERENCES weekly_schedule(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'shift'::regclass AND conname = 'ck_shift_time_range') THEN
        ALTER TABLE shift ADD CONSTRAINT ck_shift_time_range CHECK (start_time < end_time);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'shift'::regclass AND conname = 'ex_shift_employee_time') THEN
        ALTER TABLE shift ADD CONSTRAINT ex_shift_employee_time
            EXCLUDE USING gist (
                employee_id WITH =,
                tsrange(shift_date + start_time, shift_date + end_time, '[)') WITH &&
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_shift_employee_date ON shift(employee_id, shift_date);
CREATE INDEX IF NOT EXISTS ix_shift_schedule_date ON shift(weekly_schedule_id, shift_date);

COMMIT;
