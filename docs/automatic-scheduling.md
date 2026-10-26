# Automatic scheduling

Implemented on feature/automatic-scheduling using the existing Spring Boot/JWT, weekly schedule, shift service, PostgreSQL exclusion constraint and React calendar architecture. No Flyway setup, new dependency, table, entity or SQL migration was introduced. Staffing requirements are request-only and are not persisted.

## Manager workflow

Open an admin draft week and choose **Generate schedule**. Choose FIXED exact shifts or FLEXIBLE staffing windows per requirement, then enter an integer maximum weekly-hour limit and the fairness preference. Flexible requirements also use configurable minimum/maximum generated-shift minutes. Generate previews without saving shifts. Review each staffing target, existing coverage, proposed coverage and shortages, plus existing/proposed/total weekly hours per employee. Review the preview calendar (selected suggestions use dashed cards; existing shifts remain visible), select the suggestions to keep, review the selection, then confirm **Apply to draft**. The existing calendar and assigned-shift list refresh. Publishing remains the separate existing action; published weeks have no generation controls and both backend operations reject them.

Requirements must be inside the selected Monday–Sunday week, with whole-second times and start before end. Overnight shifts are not supported. Adjacent requirements are accepted; overlapping or duplicate intervals on one day are rejected to avoid ambiguous staffing semantics. Bounds: 1–100 requirements, 1–50 employees per requirement, at most 1000 requested slots, and maxWeeklyHours from 1 to 168. Fairness is a preference, never an override of availability/overlap/hour constraints. Existing over-limit assignments are preserved, shown in workload totals, and receive no additional hours; the UI flags that condition.

## Flexible windows: distinct employees, not simultaneous coverage

FLEXIBLE means: request N distinct employees to work **sometime** during the specified window. It does **not** mean N employees continuously or simultaneously present. For exact full-interval staffing use FIXED (the default when mode is omitted). This is not a continuous-coverage optimizer.

For a Monday window 09:00–17:00 requesting three employees, availability A 09:00–14:00, B 10:00–16:00 and C 14:00–17:00 can yield those three individual shifts. Their times differ and different employees may overlap. Missing availability is never treated as all-day availability. The existing model supports only **one recurring availability interval per employee per weekday**; this feature adds no schema support for multiple intervals.

Optional request-level `minShiftMinutes` and `maxShiftMinutes` default to **120 and 480** (two/eight hours). They must be integers satisfying `1 <= minimum <= maximum <= 1440`. They constrain **new FLEXIBLE shifts only**; FIXED exact times remain unchanged, even for shifts shorter than the flexible minimum. Provided duration settings are validated even if all rows are fixed. These defaults are adjustable planning preferences, not legal, break or organization-specific policy.

For each flexible window, count distinct employees with any positive-duration existing overlap once; never give those employees an extra shift just to fill the same distinct count. Existing shorter shifts still count: new minimum durations do not retroactively invalidate stored assignments. If existing workers already exceed the requested target, preserve all existing shifts, suggest no additional workers, and report that condition. No more new distinct workers are proposed than remaining places.

For other employees, intersect the window with their recorded weekday availability and subtract occupied intervals. Round fractional availability inward to whole seconds so generated times remain persistable without extending availability. Choose one contiguous free interval per employee, beginning at its earliest feasible time and using the longest duration allowed by the fragment, maximum duration and remaining weekly-hour budget. Discard fragments shorter than the minimum; do not join across busy periods, fabricate availability, or create tiny shifts to eliminate shortages.

Rank eligible candidates by current weekly seconds (existing plus earlier proposals) when fairness is enabled; then prefer longer feasible duration, earlier start, and UUID string order. With fairness off, omit only the weekly-load ranking. These are deterministic greedy priorities, not a claim of globally balanced/optimal results. Requirements are processed chronologically; assignments within a flexible requirement follow scoring order, while the calendar displays actual start/end times.

If the window/availability/free time/remaining budget cannot provide another distinct employee a contiguous shift meeting the minimum, return an explicit shortage and explanation. Hour limits use exact seconds; rounded workload displays never authorize extra work. Apply rechecks window membership and flexible duration limits from the server-stored request before the existing ShiftService validation.

### Flexible API example

Use the same preview endpoint and the same `startTime`/`endTime` fields; they denote window boundaries when `mode` is FLEXIBLE:

```json
{
  "requirements": [
    { "date": "2026-10-26", "startTime": "09:00", "endTime": "17:00", "requiredEmployees": 3, "mode": "FLEXIBLE" }
  ],
  "maxWeeklyHours": 40,
  "balanceHoursFairly": true,
  "minShiftMinutes": 120,
  "maxShiftMinutes": 480
}
```

Example response excerpt (actual UUIDs/token/expiry are returned by the server):

```json
{
  "assignments": [
    { "suggestionId": "0:employee-b-uuid", "requirementIndex": 0, "employeeId": "employee-b-uuid", "displayName": "Employee B", "shiftDate": "2026-10-26", "startTime": "10:00:00", "endTime": "16:00:00" },
    { "suggestionId": "0:employee-a-uuid", "requirementIndex": 0, "employeeId": "employee-a-uuid", "displayName": "Employee A", "shiftDate": "2026-10-26", "startTime": "09:00:00", "endTime": "14:00:00" },
    { "suggestionId": "0:employee-c-uuid", "requirementIndex": 0, "employeeId": "employee-c-uuid", "displayName": "Employee C", "shiftDate": "2026-10-26", "startTime": "14:00:00", "endTime": "17:00:00" }
  ],
  "coverage": [
    { "requirementIndex": 0, "mode": "FLEXIBLE", "date": "2026-10-26", "startTime": "09:00:00", "endTime": "17:00:00", "requiredEmployees": 3, "existingEmployees": 0, "suggestedEmployees": 3, "shortage": 0, "explanation": "Distinct employees during the window; simultaneous coverage is not guaranteed. Target met." }
  ]
}
```

The full response still includes the token, expiry, schedule ID and all workload totals. Apply remains unchanged: submit only `previewToken` and selected `suggestionIds`. Duration preferences/mode are bound to the cached server request, not trusted from an apply payload. Unknown mode or invalid duration limits return 400. Requirements may mix FIXED and FLEXIBLE but must still not overlap on the same date, to avoid ambiguous counting. Omitting mode preserves the original fixed API and Java callers.

## APIs

Both endpoints use existing `/api/admin/**` JWT authorization: ADMIN only. Unauthorized roles receive 403; missing/invalid authentication receives 401. Responses use DTOs with IDs, display names, dates/times, coverage and workload totals; no password/hash, email, phone or other account data is returned.

POST `/api/admin/schedules/{id}/generate-preview` (200):

```json
{
  "requirements": [
    { "date": "2026-10-26", "startTime": "09:00", "endTime": "17:00", "requiredEmployees": 3 },
    { "date": "2026-10-27", "startTime": "10:00", "endTime": "18:00", "requiredEmployees": 2 }
  ],
  "maxWeeklyHours": 40,
  "balanceHoursFairly": true
}
```

The response contains:

```json
{
  "previewToken": "opaque-server-generated-uuid",
  "scheduleId": "the-week-uuid",
  "expiresAt": "2026-10-26T10:10:00Z",
  "assignments": [
    {
      "suggestionId": "0:employee-uuid",
      "requirementIndex": 0,
      "employeeId": "employee-uuid",
      "displayName": "Alex Example",
      "shiftDate": "2026-10-26",
      "startTime": "09:00:00",
      "endTime": "17:00:00"
    }
  ],
  "coverage": [
    {
      "requirementIndex": 0,
      "date": "2026-10-26",
      "startTime": "09:00:00",
      "endTime": "17:00:00",
      "requiredEmployees": 3,
      "existingEmployees": 1,
      "suggestedEmployees": 1,
      "shortage": 1,
      "explanation": "Not enough employees satisfy full availability, non-overlap and weekly-hour limits",
      "mode": "FIXED"
    }
  ],
  "workloads": [
    { "employeeId": "employee-uuid", "displayName": "Alex Example", "existingWeeklyHours": 8.00,
      "proposedWeeklyHours": 8.00, "resultingWeeklyHours": 16.00 }
  ]
}
```

This response is an abbreviated shape example, not a complete response to the two-row request above; actual UUIDs, coverage rows and assignments depend on the current database. requirementIndex is the zero-based index in the submitted requirements. Suggestions are ordered chronologically, then by deterministic ranking. Token and expiration metadata change between previews; assignment/coverage/workload decisions are deterministic for identical scheduling inputs.

POST `/api/admin/schedules/{id}/apply-generated` (200, list of created ShiftResponse records):

```json
{
  "previewToken": "the-actual-preview-token",
  "suggestionIds": ["0:the-actual-employee-uuid"]
}
```

Copy the actual opaque token and suggestion IDs from the response. The client sends no employee IDs, dates or times as authoritative assignments. Empty selections, duplicate IDs or unknown/tampered IDs are rejected (400). A successful application consumes the entire token, even when a subset was selected. Generate another preview to staff remaining demand. Neither endpoint publishes or edits/deletes existing shifts.

Missing schedules return 404. Invalid intervals, week dates, counts, request limits and selections return 400. Published schedules, stale/expired/evicted/used tokens, different admins/weeks, concurrent lock failures or newly conflicting assignments return 409. Generate and review a new preview on a 409. Database/service failures abort the transaction rather than partially applying suggestions.

## Fixed-mode algorithm and common constraints

1. Validate the request, rejecting overlapping intervals and bounding input size.
2. Read all employees, recurring availability and every existing shift dated within the selected week, including published assignments. Preview uses a read-only repeatable-read transaction and writes nothing to PostgreSQL.
3. Sort requirements by date/start time. Sort employees by UUID string as the deterministic tie-breaker.
4. In FIXED mode, count existing employees whose shifts continuously cover the full requirement. Adjacent records for the same employee are merged logically for coverage; a gap does not count as full coverage. Distinct employees are counted once. Existing assignments are never deleted or rewritten. If coverage already meets/exceeds the target, add no shifts.
5. Consider candidates fully available for the weekday/interval, with no overlap against existing or newly suggested shifts, and enough remaining weekly hours. Intervals are half-open `[start,end)`: touching endpoints do not overlap, matching the native PostgreSQL exclusion constraint.
6. With fairness enabled, prefer the fewest current scheduled seconds (existing plus prior suggestions), breaking ties by UUID. With fairness off, use UUID order alone while retaining every hard constraint. Assign each employee at most once per requirement and update occupied intervals/workload before the next requirement.
7. Return partial suggestions and explicit shortages if there are too few eligible employees. Never extend availability or exceed the configured limit to meet demand. An employee partially covering an interval cannot fill a newly generated full-interval assignment; existing partial work is preserved. FIXED mode does not shorten exact intervals. FLEXIBLE uses the window-specific procedure above, with one contiguous suggestion per selected employee and requirement.

All comparisons and hour limits use exact whole seconds. Hour totals are rounded to two decimal places only for display. No historical balancing, minimum hours, preferences, skills, location constraints or time-off decisions are introduced. Greedy allocation is **not globally optimal**: an early flexible assignment can leave later demand unfilled even where a more complex optimizer could find a better solution. Shortage explanations identify the hard-constraint categories; they are not a global infeasibility proof or per-employee diagnosis.

Let R be requirements, E employees, A availability rows, S existing weekly shifts and P proposed assignments (bounded by 1000). A conservative worst-case CPU bound for the straightforward scans/coverage/free-fragment sorting is O(R² + E*S + R*E*(log(E) + A + (S+P)*log(1+S+P))); memory is O(E + A + S + P), plus a bounded preview store. Snapshot fingerprint sorting adds O((E+A+S) log(E+A+S)). This is intended for small teams/weeks; indexed candidate/interval lookups are a useful next improvement before large rosters. Sorting and rejecting ambiguous inputs keep the initial behavior explainable.

## Tokens, stale state and concurrency

The server stores immutable suggestions/request options under a cryptographically random UUID, bound to the authenticated admin subject and schedule. Tokens expire after ten minutes, with at most eight previews per admin and 256 globally; oldest previews can be evicted. No token or staffing requirement is written to the database.

The preview fingerprint hashes employee IDs, all recurring availability rows (IDs, employee IDs, weekdays and times), schedule identity/week/status, and all existing weekly assignment IDs, schedule/employee IDs, dates and times. Changes anywhere in that snapshot invalidate the token, even if unrelated to a selected suggestion. Display-name-only changes do not affect eligibility and are not fingerprinted. This conservative approach trades extra regeneration for simple stale-state handling.

Apply first acquires the existing weekly-schedule write lock used by manual edits and publication. It then obtains shared employee and availability row locks in stable order and compares the fresh fingerprint. Selected suggestions come exclusively from the server cache. It rechecks stored window/duration policy and exact weekly hours and invokes **ShiftService.create** for every assignment in the same transaction, retaining full availability/date/overlap validation and the PostgreSQL exclusion constraint. Shared eligibility locks prevent concurrent updates/deletes of the rows used during application; the schedule lock serializes all edits/publication for that week. New employees/availability can be added concurrently but cannot invalidate an already eligible selected assignment.

The batch either commits entirely or rolls back entirely. Tokens are consumed only after successful commit, so a rolled-back database failure can be retried if state still matches. Concurrent applications serialize: the first commits, the second sees an already-used token or changed snapshot and receives 409. Publication races use that same schedule lock; either application commits before publication or it is refused after publication. No schema or constraint changes are needed.

Preview does not reserve employees. A preview may complete from its consistent snapshot while another transaction publishes/changes the week; apply always checks current locked state. Expiration is checked when apply begins. Existing assignments that no longer match current availability or already exceed the requested cap are not retroactively fixed; they remain the manager's responsibility and do not authorize new invalid work.

The cache is deliberately single-instance and nonpersistent. Restart or routing apply to a different process requires a new preview. Multi-instance hosting would need sticky routing or a separately reviewed shared/signed preview design; no deployment or new caching dependency is added here. UUID tokens are references, not substitutes for JWT ADMIN authorization or owner/week checks.

## Tests and operational scope

JUnit/Mockito tests cover deterministic planning, availability/partial coverage, adjacency, overlaps, existing coverage (including merged adjacent records), weekly caps with exact seconds, fairness, empty rosters, shortages, request bounds, token expiration/eviction/binding, stale state and published restrictions. MVC tests exercise JWT role rules, input validation and conflict responses. Disposable PostgreSQL HTTP tests cover read-only previews, selected application, native constraints, stale/tampered requests, second-insert rollback/retry, concurrent applications and publication races. React tests cover forms, selection/confirmation, shortages, loading/empty/error/expiry states, published controls, API contracts and calendar refresh.

Run backend checks using the existing Maven wrapper and guarded disposable `availability_test` JDBC properties. Run frontend `npm run lint`, `npm run typecheck`, `npm test` and `npm run build`. Existing CI workflows automatically discover the new tests. All migration scripts/build dependencies remain unchanged. Test-only trigger fixtures used to verify rollback never run on an application database.

Future work could index candidate lookups, support continuous-coverage constraints, and add separately reviewed organization-specific duration/break policies. Multi-interval daily availability would require its own approved model/migration. A constraint optimizer could be evaluated once those requirements are defined; no global optimality is claimed. No LLM, payroll, shift swap, time-off approval, notifications, automatic publishing, migrations, deployment or CD is included.

Flexible-mode tests additionally cover staggered/different durations, cross-employee overlap, minimum/maximum durations, missing/outside availability, exact remaining-hour truncation, fractional availability rounding, distinct existing-worker counting, configurable-duration cache binding, flexible atomic rollback/retry, concurrency and frontend mode switching/preview calendar behavior.

## Optional AI instructions

The same manual/preview/apply flow supports interpreting manager text through a backend-only Groq provider. Read [AI staffing instructions](llm-scheduling.md) for configuration, interpretation rules, nullable policies, review gates and limitations. Interpretation never writes assignments or publishes.
