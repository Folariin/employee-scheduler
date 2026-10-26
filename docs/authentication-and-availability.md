# Authentication persistence, identity and admin availability

## Root causes

1. Frontend session.ts kept its state only in a module variable. Refresh destroyed it even though the JWT was still valid. The backend already validated signed JWTs correctly; no authentication shortcut was needed.
2. Only employee self-service availability routes existed. Scheduling could read availability internally, but administrators had no dedicated read endpoint or employee-directory entry point.
3. Login returns token and role, with no employee names. The layout/dashboard intentionally rendered generic labels; there was no authenticated self-profile endpoint. Names must come from the user/employee relationship, never guessed from email.
4. The employee overview's unequal columns and small-screen collapse reduced usable desktop space. It now uses equal flexible columns inside the existing full-width workspace, with a single column below 900px.

## Changes

- Store only token and role in tab-scoped sessionStorage under employee-scheduler.session.v1. Restore synchronously at module initialization, before React protected routes evaluate. Validate structure, subject, role consistency and expiration before accepting storage. Corrupted/expired stored sessions are removed. JWT decoding is for UI state only, not signature verification.
- Existing API client sends restored bearer tokens. Expiry timers and API preflight checks still reject expired sessions. Server 401 removes memory and storage; 403 preserves the session. Logout removes the stored session and existing session listeners clear React Query private data.
- Storage failures gracefully fall back to in-memory login. In that situation refresh persistence cannot be guaranteed.
- GET /api/employees/me returns only firstName and lastName, resolved from the authenticated JWT user subject via EmployeeService. EMPLOYEE only; no client-selected ID. Missing profile returns 404 and the UI retains generic labels.
- Employee layout and dashboard share a user-keyed React Query self-profile request. Greeting uses first name, sidebar/top-right show full name, role remains separate. Profiles are fetched again after page reload rather than persisted as stale identity information.
- GET /api/admin/employees/{id}/availability checks employee existence and returns the existing availability DTOs ordered Monday–Sunday. ADMIN only; no write methods. Missing employee returns 404; existing employee with no intervals returns []. Employee self-service routes and restrictions remain unchanged.
- Employee directory includes an Availability action, opening a read-only seven-day dialog with weekday/time ranges, explicit unset/unavailable days, loading, error and empty states.

## Security tradeoffs

sessionStorage preserves the existing bearer-JWT API without new cookie/refresh-token infrastructure. It survives reload and is scoped to the tab's origin/session; localStorage and passwords are not used. It is not HttpOnly: XSS could read the token, as it could access the previous runtime token. Keep scripts trusted, avoid unsafe HTML, use HTTPS and appropriate CSP in production. A newly opened tab may inherit a copy from its opener; use noopener for app-created new-tab links. Browser restore behavior can retain tab storage. Logout clears only this tab's local token; it does not revoke an issued stateless JWT elsewhere. No refresh token or server-side revocation was added.

The frontend cannot verify a JWT signature from storage. A tampered token may temporarily affect local navigation, but real data requests are always authorized by existing server signature/expiry/role validation and a 401 clears the session. Never treat client routing as the authorization boundary. The application does not extend token expiry on refresh.

## Verification and scope

New frontend tests cover reload hydration, fresh module initialization, malformed/expired/role-mismatched sessions, logout removal, blocked storage, authenticated name display after restoration, read-only admin availability and empty days. Existing API tests cover bearer headers, 401 cleanup and 403 behavior. Backend MVC tests use the real JWT/security chain with mocked services for self-identity isolation, minimal response fields, admin availability, ordering, 404/401/403, invalid JWT, unsupported writes and arbitrary profile paths. Existing availability/employee/JWT tests are run as regressions.

All prior uncommitted redesign and LLM scheduling changes are preserved. No database schema, migration, scheduling algorithm, provider call, dependency or CI change. No real database operations. Browser automation was unavailable during the preceding redesign (computer-use RPC not configured), so native reload/tab-close behavior and responsive layout require a manual browser review.

## Manual checks

Sign in as each role, reload a protected page, and verify the role-specific navigation and API requests still work. For an employee confirm the first/full names come from their account. Log out, reload, and confirm login is required. Use a short-lived test token to check expiry. As admin open each employee's Availability dialog, including an employee with no intervals; check all seven weekdays and confirm no write controls. As employee, request the admin availability URL and expect 403. Use disposable test fixtures, never modify real availability just to test the interface.
