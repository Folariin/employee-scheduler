-- Run with the application stopped, against the existing pre-Stage-1 schema.
-- Additive only: legacy shift rows and the old day column are preserved.
BEGIN;

CREATE TABLE IF NOT EXISTS weekly_schedule (
    id uuid PRIMARY KEY,
    week_start_date date NOT NULL,
    status varchar(255) NOT NULL DEFAULT 'DRAFT',
    created_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_weekly_schedule_start UNIQUE (week_start_date),
    CONSTRAINT ck_weekly_schedule_monday CHECK (extract(isodow FROM week_start_date) = 1),
    CONSTRAINT ck_weekly_schedule_draft CHECK (status = 'DRAFT')
);

ALTER TABLE shift ADD COLUMN IF NOT EXISTS weekly_schedule_id uuid;
ALTER TABLE shift ADD COLUMN IF NOT EXISTS shift_date date;

COMMIT;
