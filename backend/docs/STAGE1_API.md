# Stage 1: draft weekly schedules and shifts

All routes below require the existing JWT bearer authentication and ADMIN role.
EMPLOYEE users receive 403; absent/invalid authentication receives 401. No security
configuration, authentication mechanism, publishing, or employee-facing shift API
was added. Managers use the project's existing ADMIN role.

| Method | Route | Success |
|---|---|---|
| POST | `/api/admin/schedules` | 201, schedule summary |
| GET | `/api/admin/schedules` | 200, newest week first |
| GET | `/api/admin/schedules/{id}` | 200, schedule plus shifts |
| POST | `/api/admin/schedules/{scheduleId}/shifts` | 201, shift |
| PUT | `/api/admin/shifts/{id}` | 200, updated shift |
| DELETE | `/api/admin/shifts/{id}` | 204, empty body |

Send `Content-Type: application/json` for POST/PUT and `Authorization: Bearer <JWT>`.
IDs below are illustrative; use the actual IDs returned by creation.

## Weekly schedule

POST `/api/admin/schedules`:

```json
{"weekStartDate":"2026-10-26"}
```

201 response:

```json
{
  "id":"11111111-1111-1111-1111-111111111111",
  "weekStartDate":"2026-10-26",
  "status":"DRAFT",
  "createdAt":"2026-10-21T12:00:00Z"
}
```

The date must be a Monday in years 1–9999. Each Monday has one schedule, enforced
by a database unique constraint. A repeated Monday returns 409, including races.
Only DRAFT exists in this stage. Status and timestamps are server controlled;
there are no schedule status-update, publish, or schedule-delete endpoints.
GET collection returns an array of summaries (or `[]`). GET detail returns the
summary fields plus `shifts`, ordered by date, start time, then ID:

```json
{
  "id":"11111111-1111-1111-1111-111111111111",
  "weekStartDate":"2026-10-26",
  "status":"DRAFT",
  "createdAt":"2026-10-21T12:00:00Z",
  "shifts":[
    {
      "id":"33333333-3333-3333-3333-333333333333",
      "scheduleId":"11111111-1111-1111-1111-111111111111",
      "employeeId":"22222222-2222-2222-2222-222222222222",
      "shiftDate":"2026-10-26",
      "startTime":"10:00:00",
      "endTime":"16:00:00"
    }
  ]
}
```

## Assign, update, and remove shifts

POST `/api/admin/schedules/11111111-1111-1111-1111-111111111111/shifts`:

```json
{
  "employeeId":"22222222-2222-2222-2222-222222222222",
  "shiftDate":"2026-10-26",
  "startTime":"10:00",
  "endTime":"16:00"
}
```

201 response is the six-field shift object shown inside `shifts` above. PUT
`/api/admin/shifts/33333333-3333-3333-3333-333333333333` accepts the same complete
request DTO and returns that object with updated fields and the same shift ID.
It can change employee, date, or hours within the original schedule. It cannot
move the shift to a different schedule. DELETE removes only that shift, leaving
the employee, user account, and weekly schedule intact.

## Validation and errors

- 400: missing required fields, malformed JSON/UUID/date/time, a non-Monday
  schedule, shift date outside its Monday–Sunday week, equal/reversed/overnight
  times, or fractional-second shift times. The inspected legacy schema uses
  `time(0)`; accepting fractions would silently round assignments, so shift input
  is limited to whole seconds (`HH:mm` and `HH:mm:ss`).
- 404: missing schedule, employee, or shift.
- 409: duplicate weekly schedule, no recurring availability on the shift weekday,
  partial/complete availability mismatch, overlap with another shift for that
  employee, or a concurrent shift change requiring retry.
- Availability boundaries are inclusive for coverage: 09:00–17:00 availability
  permits a 09:00–17:00 shift. A shift must fit entirely inside it.
- Overlap uses half-open intervals: `existingStart < proposedEnd` and
  `existingEnd > proposedStart`. Adjacent shifts are allowed; different employees
  can work simultaneously. Update excludes its own ID from the precheck.
- Controllers return DTOs, never JPA entities or account/password data. Error
  bodies retain the project's Spring Boot/ResponseStatusException conventions;
  status codes are the stable contract.

## Concurrency and limits

Service transactions and row locking serialize updates/deletes of the same
shift. Friendly overlap queries catch ordinary conflicts, while PostgreSQL's
GiST exclusion constraint on `(employee_id, tsrange(date+start,date+end,'[)'))`
prevents conflicting commits even when application prechecks race or are bypassed.
Exclusion violations and transient deadlock victims become 409 with transaction
rollback. Clients may retry a concurrent-change response; they must not assume
that both competing requests succeeded. Concurrent schedule creation is protected
by the unique Monday constraint, with 409 after a losing repository transaction.

Apply the [migration instructions](../db/MIGRATION.md) before starting the app.
The schema validator refuses incomplete native constraints. Availability is checked
at assignment/update time; later availability edits do not automatically rewrite
existing shifts. Dates/times are local calendar values; timezone conversion,
overnight shifts, time-off conflict integration, publishing, employee visibility,
swaps, notifications, and automatic scheduling remain outside Stage 1.
