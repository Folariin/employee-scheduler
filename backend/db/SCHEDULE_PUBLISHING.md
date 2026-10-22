# Stage 2 API

All endpoints require the existing Bearer JWT. Missing/invalid authentication is
401; an authenticated user with the wrong role receives 403.

| Endpoint | Role | Success |
| --- | --- | --- |
| POST /api/admin/schedules/{id}/publish | ADMIN | 200 WeeklyScheduleResponse |
| GET /api/shifts/me | EMPLOYEE | 200 array of ShiftResponse |
| GET /api/schedules/me | EMPLOYEE | 200 array of WeeklyScheduleDetailResponse |

Publication is a one-way DRAFT to PUBLISHED transition. A missing schedule is
404; a repeated publication is 409. Publishing an empty schedule is allowed.
Publication changes only status; IDs, dates, creation time, and assignments remain.
Existing admin list/detail endpoints also return published schedules.

Every shift creation, update, reassignment, and deletion takes the same exclusive
schedule row lock used by publication, inside the enclosing service transaction.
Update/delete first read only the schedule UUID, then lock schedule before shift.
This order avoids loading stale schedule status before a competing publication
commits and keeps a consistent lock order. If a draft edit wins the lock, it
commits before publication; if publication wins, the edit observes PUBLISHED and
returns 409. Mutations within one week serialize; different weeks remain
independent except existing employee-overlap constraints. Database row locks last
until commit/rollback. Direct SQL writers must respect this protocol; immutable
publication is enforced by the application service, not database triggers.

Employee identity comes exclusively from the JWT principal through EmployeeService.
The database query filters employee ID and PUBLISHED status together. Neither
endpoint accepts an employee selector. Existing response DTOs are reused; weekly
responses contain only that employee's matching shifts and no user account data.
Schedules with no matching own assignments are omitted, including published empty
weeks. Employees without any assignments receive []. Missing employee profiles
retain the existing EmployeeService 404 behavior.

Both employee endpoints accept optional `startDate` and `endDate` as ISO dates
(e.g. `?startDate=2026-10-26&endDate=2026-11-01`). Bounds are inclusive; either may
be omitted. Invalid dates, reversed ranges, and years outside 1..9999 are 400.
Filtering applies to shift dates, so a weekly response may contain part of a week.
Shifts are ordered by date, start time, then UUID; weekly responses appear in
ascending week order. Existing local-date/local-time semantics remain unchanged;
there is no timezone conversion or overnight-shift support.

## Verification

Use Java 21 from `backend/`:

```sh
./mvnw --batch-mode --no-transfer-progress clean test \
  -Davailability.test.jdbc-url=jdbc:postgresql://127.0.0.1:5432/availability_test \
  -Davailability.test.jdbc-user=postgres \
  -Davailability.test.jdbc-password=TEST_ONLY_PASSWORD
```

Use a disposable `availability_test` database only: integration tests create/drop
application tables. Without the JDBC property PostgreSQL suites are intentionally
skipped. Existing GitHub Actions supplies PostgreSQL and the JDBC properties, so
it automatically runs Stage 2 tests too. Test initialization applies the actual
002 and 003 SQL before the production schema guard runs. Independent migration
tests exercise 001 -> 002 -> 003, preserved legacy records, rollback, and reruns.
