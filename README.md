# Employee Scheduler

A Spring Boot scheduling backend and a React frontend for administrators and employees.

## Prerequisites

- Backend: Java 21, PostgreSQL with Stage 1 and Stage 2 migrations applied.
- Frontend: Node.js **20.19+** (20.x) or **22.12+**, with npm; Node 24 LTS is recommended.
  Node 20.12 is too old for Vite 8. The frontend was verified with Node 24.
- Existing ADMIN / EMPLOYEE accounts in the backend. No sample credentials or fake
  authentication are bundled into the frontend.

## Start locally

1. Configure and start the existing backend in `backend/`. Supply your own database
   credentials and JWT secret through private environment configuration. Follow
   [backend/db/MIGRATION.md](backend/db/MIGRATION.md) and apply migrations 001, 002,
   and 003 before startup. Keep `spring.jpa.hibernate.ddl-auto=validate` for real data.

   ```sh
   cd backend
   ./mvnw spring-boot:run
   ```

   On Windows use `mvnw.cmd`. Spring Boot defaults to port 8080. For an existing
   database, use its configured account provisioning; admin bootstrap is disabled
   by default. Never place real bootstrap passwords or JWT secrets in source control.

2. In another terminal:

   ```sh
   cd frontend
   npm ci
   cp .env.example .env.local
   npm run dev
   ```

   On PowerShell use `Copy-Item .env.example .env.local` instead of `cp` if preferred.
   Open `http://localhost:5173`. The development server binds to loopback and uses a
   strict port so a conflict is reported rather than silently selecting another port.

3. Sign in with an existing backend account. ADMIN opens the team dashboard;
   EMPLOYEE opens their personal dashboard. A full page reload requires signing
   in again because bearer tokens are deliberately kept only in memory.

## Environment variables

See [frontend/.env.example](frontend/.env.example). Environment files other than
that example are ignored by Git.

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `/api` | Public browser API prefix, including `/api` |
| `BACKEND_PROXY_TARGET` | `http://127.0.0.1:8080` | Vite-only proxy destination |

The default dev proxy forwards `/api` unchanged to Spring Boot. This avoids browser
cross-origin requests and does not require backend CORS changes. If you configure a
separate absolute API origin, allow the frontend origin in the existing backend CORS
configuration (`app.cors.allowed-origins`). All `VITE_` values are public build-time
settings: never put secrets in them. Restart Vite after environment changes.

## Pages

- **Login:** email/password validation, invalid credential feedback, role redirects.
- **Admin dashboard:** real employee/schedule/shift totals, upcoming weeks, quick
  actions, and a weekly calendar preview.
- **Employees:** directory, local search, details, creation, edit, confirmed deletion.
  Email and employee number are immutable through the current update DTO.
- **Schedules:** status filters, Monday-only creation, weekly details, shift
  assignment/update/reassignment/removal, and confirmed publication. Published weeks
  have no editing controls.
- **Employee dashboard:** upcoming assignments and summaries from published schedules.
- **My Schedule:** previous/next week navigation, personal calendar and upcoming/past/all
  shift lists. No client-supplied employee selector is sent.
- **My Availability:** one recurring interval per weekday with add/edit/remove controls.
  Changing availability does not change already assigned shifts.

Desktop uses a navy sidebar and a seven-day calendar with separate lanes for concurrent
assignments. Mobile uses a collapsible keyboard-accessible sidebar and a readable daily
agenda. Forms use native labels, validation, focus states, and modal dialog focus handling.

## Frontend architecture

```text
frontend/src/
  api/                       typed fetch client and endpoint functions
  components/calendar/       weekly grid, week navigation, shift list
  components/layout/         role-specific sidebar and application shell
  components/ui/             buttons, badges, dialogs, feedback, form helpers
  features/auth/             in-memory session, login, route guards, expiry handling
  features/schedules/        create/edit shift form
  hooks/                     shared mutation and query invalidation
  pages/                     admin and employee screens
  types/                     backend request/response contracts
  utils/                     local date/time and calendar helpers
  test/                      Vitest + React Testing Library tests
```

React + TypeScript + Vite, Tailwind CSS with shared component CSS, React Router,
TanStack Query, native fetch, and Lucide icons. No additional calendar library was
needed. There are no runtime mock APIs, hardcoded record IDs, or demo-data fallbacks.
Server state is cached and refreshed after mutations and on window focus. Failed
mutations also invalidate data, so a concurrent publication refreshes the read-only
state. The current API has no schedule-count endpoint; dashboard totals therefore
load the schedule list and each detail rather than invent counts.

## Integrated API contracts

| Method | Endpoint | Use |
| --- | --- | --- |
| POST | `/api/auth/login` | `{email,password}` -> `{token,role}` |
| GET, POST | `/api/admin/employees` | list/create employee |
| GET, PUT, DELETE | `/api/admin/employees/{id}` | details/update/delete |
| GET, POST | `/api/admin/schedules` | list/create weekly draft |
| GET | `/api/admin/schedules/{id}` | full admin weekly detail |
| POST | `/api/admin/schedules/{id}/publish` | publish draft |
| POST | `/api/admin/schedules/{id}/shifts` | assign shift |
| PUT, DELETE | `/api/admin/shifts/{id}` | update/reassign/delete shift |
| GET | `/api/shifts/me` | personal published shifts |
| GET | `/api/schedules/me` | personal published weeks and own shifts |
| GET, POST | `/api/availability/me` | own recurring availability |
| PUT, DELETE | `/api/availability/me/{dayOfWeek}` | update/remove own interval |

Employee date filters use ISO `startDate` and `endDate`, both inclusive. Weekly
navigation requests Monday through Sunday. Empty published weeks without personal
assignments are omitted by the backend. Availability uses MONDAY through SUNDAY,
with the PUT body day matching the URL. Times are local whole-second values; no
frontend timezone conversion or overnight-shift support is introduced.

## Authentication and temporary passwords

JWTs live in module memory only, never localStorage, sessionStorage, URL parameters,
or logs. Expiry timers, focus checks, and 401 responses clear the session and private
query cache; logout does the same. Tabs are independent and a reload logs out. There
is no refresh-token endpoint, so no silent renewal is attempted. Claims are decoded
only for UX/expiry; the backend remains responsible for signature and role validation.

Login and employee creation use direct transient requests rather than retaining
passwords in TanStack mutation variables. Temporary passwords are masked while
entered and discarded when creation succeeds/dialog closes; they are not returned,
displayed afterward, or persisted. An admin must share the initial password securely
before submitting. The current backend has no first-login password-change/reset API;
this UI does not invent one or imply the password automatically expires.

## Verification commands

From `frontend/`:

```sh
npm test
npm run lint
npm run typecheck
npm run build
npm run preview
```

`npm test` runs Vitest and React Testing Library without a backend/database. Test
responses are mocked only inside `src/test`. The tests exercise authentication,
role-specific navigation, cache clearing, calendar rendering, shift validation,
publication/read-only behavior, personal endpoint usage, availability forms, and
loading/error states. `npm run build` runs TypeScript before bundling into `dist/`.
Preview is a local build check, not a deployment configuration. Any future static
host must provide SPA history fallback and route `/api` to the backend; deployment
and CI/CD are outside this stage.

Backend tests and their PostgreSQL setup remain unchanged. See
[backend/db/SCHEDULE_PUBLISHING.md](backend/db/SCHEDULE_PUBLISHING.md) for the complete
Maven command and guarded disposable `availability_test` database requirement.

## Current integration limits

- No admin endpoint exposes employee availability, so there is no admin availability
  page; shift conflicts are reported by the existing service.
- Backend error bodies may omit detailed validation reasons. Server-provided messages
  are shown when present; otherwise the UI gives status-specific guidance. It never
  guesses that a particular validation condition definitely caused a conflict.
- Employee profile/name lookup is unavailable to employees, so personal screens use
  neutral wording and “Your shift” rather than requesting admin employee records.
- No job titles, locations, trend percentages, notification badges, payroll, attendance,
  settings, or profile-edit controls are fabricated from the visual mockups.
- Existing employee deletion can be rejected if related database records still exist.
- Lists/counts are unpaginated because the existing APIs are unpaginated. Dashboard
  detail fetching may need a backend aggregate endpoint if data grows substantially.
- Upcoming/past list tabs classify by local calendar date, consistent with the API’s
  date-filter semantics; today’s shifts remain in Upcoming for the whole day.

No backend functionality, database migrations, CI/CD, or deployment configuration was
changed for Stage 3.

## Automatic scheduling

Admins can generate and review staffing suggestions for draft weeks, then apply selected assignments without publishing. See [automatic scheduling](docs/automatic-scheduling.md) for API examples, validation, greedy fairness behavior, concurrency, preview-token limits and tests. This feature adds no database migration or persistent staffing-requirement table.

### Flexible generated shifts

Automatic scheduling now supports per-requirement `FIXED` (default) and `FLEXIBLE` modes. Fixed specifies exact times; flexible treats them as a staffing window and chooses individual times within recorded availability. A flexible count means **distinct employees working sometime in the window, not simultaneous or continuous coverage**. Defaults for new flexible shifts are 120–480 minutes and can be configured per preview. Existing assignments, weekly caps, secure preview/apply, and published-week restrictions remain intact. See [scheduling documentation](docs/automatic-scheduling.md) for staggered examples, scoring, shortages, one-interval-per-day availability limitations and future continuous-coverage/policy work. No database migration or new table was added.
