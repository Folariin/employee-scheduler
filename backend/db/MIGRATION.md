# Stage 1: migrate legacy shifts safely

These are explicit PostgreSQL migrations, not automatically executed Flyway
migrations. No migration framework dependency was added. Stop every application
instance for expansion, backfill, and finalization. Back up and rehearse on a copy
of the actual database first. Do not run Hibernate `update`, `create`, or
`create-drop` against an existing database to migrate these mappings.

## Inspected baseline and assumptions

The existing mappings were exercised against an isolated PostgreSQL 17 database
before editing them. Hibernate generated the legacy table as:

```sql
CREATE TABLE shift (
    end_time time(0), start_time time(0), employee_id uuid,
    id uuid NOT NULL PRIMARY KEY, day varchar(255)
);
-- The existing schema also has an employee_id foreign key to employees(id).
```

There was no date or weekly schedule. Employee and time columns were nullable.
Actual deployed databases may differ: inspect `\d+ shift`, existing foreign keys,
row counts, and `\d+ employees` before applying these scripts. They assume the
project's existing tables and employee foreign key are present in the current
schema/search path. For a brand-new environment, initialize the previous release's
baseline schema in an empty database first, then apply these migrations. These
scripts do not bootstrap unrelated employee/authentication/time-off tables.

## 1. Back up, stop the app, and expand

From `backend/`, with a database URL supplied privately in your environment:

```sh
pg_dump "$DATABASE_URL" --format=custom --file=before-stage1.dump
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/migration/001_expand_weekly_schedules.sql
```

The script creates `weekly_schedule` with UUID IDs, a unique Monday, DRAFT-only
status, and creation timestamp. It adds nullable `weekly_schedule_id` and
`shift_date` columns to `shift`. Existing IDs, employees, weekday text, and times
are preserved. It does not delete or truncate rows. An empty shift table needs
no backfill; proceed to finalization.

## 2. Explicitly backfill legacy rows

A legacy weekday cannot determine a historical date or week. Do not guess the
current week. Prepare a reviewed CSV with `shift_id,shift_date` and one real date
for every legacy row. Resolve null employees/times, invalid intervals, overlaps,
or inappropriate availability manually with the data owner. Check that the chosen
dates match the intended historical weekdays. The old `day` column is retained
as audit information and is ignored by the new entity.

In an interactive `psql` session connected to the target database:

```sql
BEGIN;
CREATE TEMP TABLE shift_dates (
    shift_id uuid PRIMARY KEY,
    shift_date date NOT NULL
) ON COMMIT DROP;
-- Replace this client-side CSV path with the reviewed mapping file.
\copy shift_dates FROM '/absolute/path/shift-dates.csv' WITH (FORMAT csv, HEADER true)

-- Reject unknown IDs rather than silently ignoring mapping mistakes.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM shift_dates m LEFT JOIN shift s ON s.id = m.shift_id WHERE s.id IS NULL) THEN
        RAISE EXCEPTION 'Mapping contains unknown shift IDs';
    END IF;
END $$;

INSERT INTO weekly_schedule (id, week_start_date)
SELECT gen_random_uuid(), monday
FROM (
    SELECT DISTINCT shift_date - (extract(isodow FROM shift_date)::integer - 1) AS monday
    FROM shift_dates
) weeks
ON CONFLICT (week_start_date) DO NOTHING;

UPDATE shift s
SET shift_date = m.shift_date, weekly_schedule_id = w.id
FROM shift_dates m
JOIN weekly_schedule w ON w.week_start_date = m.shift_date - (extract(isodow FROM m.shift_date)::integer - 1)
WHERE s.id = m.shift_id;
COMMIT;
```

Review counts and row values before finalization. Backfill does not invent dates,
change shift IDs, or discard assignments. Current recurring availability may differ
from historical availability; review that separately rather than silently altering
history. Future API writes always validate the current availability.

## 3. Finalize, validate, then start Stage 1

```sh
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/migration/002_finalize_dated_shifts.sql
```

Finalization is transactional and locks the two scheduling tables. It refuses
unmapped rows, missing schedules/employees/times, invalid intervals, and dates
outside the mapped week. It then installs NOT NULL constraints, the schedule
foreign key, a time-range check, lookup indexes, and the employee/time exclusion
constraint. Existing overlaps cause constraint installation to fail and roll back;
they are never deleted automatically. Correct the reviewed data and rerun.

PostgreSQL's bundled `btree_gist` extension is required for UUID equality in the
GiST exclusion constraint. The migration role needs the relevant DDL/extension
privileges; have the DBA install the extension in `public` if needed. The runtime
application user need not have extension-creation privileges. See PostgreSQL's
[range constraint documentation](https://www.postgresql.org/docs/17/rangetypes.html#RANGETYPES-CONSTRAINT)
and [btree_gist documentation](https://www.postgresql.org/docs/17/btree-gist.html).

The application now defaults to `spring.jpa.hibernate.ddl-auto=validate`. Startup
also checks the native exclusion constraint, weekly unique constraint, and required
NOT NULL columns, since Hibernate validation alone does not verify those native
constraints. Expansion alone is insufficient: finish migration before starting.
The scripts can be rerun after successful finalization.

Keep the backup and old `day` column. Rollback to the previous release requires
an operator-reviewed restore/backfill plan; there is no destructive automatic
down migration. New dated shifts do not populate the obsolete weekday column.

## Tests and fresh disposable databases

PostgreSQL integration tests create/drop ORM tables only in the guarded
`availability_test` database. A test-only initializer installs the same finalization
script before the production schema validator runs. Migration tests independently
rebuild the legacy table shape in a temporary schema and prove preservation,
refusal of unmapped/invalid rows, successful backfill, idempotence, and native
constraints. None of this runs automatically against the application database.
