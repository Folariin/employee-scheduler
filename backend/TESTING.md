# Backend tests

Run the database-free suite from `backend/` with Java 21:

```sh
./mvnw test
```

On Windows, use `mvnw.cmd test`. An installed Maven can also run `mvn test`.
No PostgreSQL server, database credentials, JWT environment variables, or seeded
admin account are needed.

- `EmployeeServiceTest`: employee creation, number conflicts, email-conflict
  propagation, list/detail lookup, missing employees, updates, response mapping,
  and employee deletion/flush before user deletion. Repositories and `UserService`
  are mocked with Mockito.
- `EmployeeAccountServiceTest`: real account-creation logic with mocked user
  repository and password encoder, including email normalization, duplicate email
  rejection, the employee role, password encoding, and user deletion.
- `EmployeeControllerTest`: a focused Spring MVC context with the production `SecurityConfig`
  and `JwtAuthFilter`. Tests cover response bodies/statuses, DTO validation,
  malformed input, and admin-only authorization on all five CRUD routes. Only
  `EmployeeService` and `JwtService` are mocked; filters are enabled. Bearer-token
  claims are controlled by the JWT mock, so these tests do not verify JWT signing
  or parsing.
- `AvailabilityServiceTest`: validation, scoped lookup/update/delete, missing
  records, duplicate checks, and PostgreSQL unique-violation translation.
- `AvailabilityControllerTest`: MVC and the production security filter chain
  with real signed JWTs, mocked services, request validation, response codes,
  employee-only access, and agreement between PUT's URL and body day.
- `JwtAuthFilterTest`: expiry during claim extraction, preservation of an
  existing authentication, and propagation of downstream application errors.
- `AvailabilityIntegrationTest`: real HTTP requests through the complete Spring
  Boot application, signed JWTs, real services/repositories, and PostgreSQL.
  Exercises CRUD persistence, cross-employee isolation, the database unique
  constraint, eight concurrent creates, status codes, and time boundaries.
  This class is skipped unless the database property below is supplied.

The generated full-application `contextLoads` test was replaced by the MVC tests
because it required PostgreSQL and runtime JWT configuration. JUnit Jupiter is
pinned to 5.14.4 in the POM to honor the project's JUnit 5 requirement; Spring Boot
4.1.1 otherwise manages JUnit 6.
The MVC context is assembled directly because Spring 7.1's JUnit extension uses
JUnit 6 APIs. This keeps every test on JUnit 5 while preserving real MVC request
handling, validation, and security filters.

Surefire loads Mockito as a Java agent at JVM startup. This avoids dynamic
Java-agent attachment during test startup on Java 21.

The database-free tests verify behavior and repository interactions. The opt-in
availability integration tests below also verify SQL uniqueness and update/delete
persistence. Before merging, run both suites in CI with Java 21. Additional
employee-management integration coverage remains useful for account/employee
transaction rollback and employee deletion with related scheduling records.

## PostgreSQL availability integration tests

Create a disposable PostgreSQL database named **availability_test**. The test
application uses `create-drop` and clears its tables between tests. Never use the
application database. The URL guard requires this database name to prevent an
accidental run against `employee_scheduler`.

Run all tests, including PostgreSQL integration tests:

```sh
./mvnw test "-Davailability.test.jdbc-url=jdbc:postgresql://127.0.0.1:55439/availability_test" "-Davailability.test.jdbc-user=postgres"
```

Use `mvnw.cmd` on Windows. Supply `-Davailability.test.jdbc-password=...` if the
disposable database requires a password. PostgreSQL may run on another port;
55439 is just the isolated local test port. No new dependencies, Docker, seeded
admin, or externally configured JWT secret are required. The application server
binds to a random port and is closed after the tests. Default `test` runs remain
database-free.

Availability accepts one interval per employee per weekday, with non-null times
and `startTime < endTime`; equal, reversed, and overnight intervals return 400.
PUT updates the specified day's times and requires its body `dayOfWeek` to match
the URL; it does not move availability between weekdays. Availability routes
require the EMPLOYEE role. An employee cannot select another owner via an ID in
the body, URL, or query string. Duplicate days return 409, and missing employee
profiles or missing days for update/delete return 404. A missing profile is
different from an empty availability list, which returns 200 with `[]`.

## GitHub Actions CI

`.github/workflows/backend-ci.yml` runs on pushes to `feature/**` branches and
pull requests targeting `main`. It uses Temurin Java 21, caches Maven dependencies,
and starts a disposable PostgreSQL 17 service with a readiness health check.
The database name is `availability_test`, matching the integration test guard.
Its credentials are test-only literals; no production secrets are needed.

The job runs the existing Maven wrapper from `backend/` with `clean test` and
the three `availability.test.jdbc-*` properties. This runs both the unit/MVC
tests and PostgreSQL integration tests. It invokes `mvnw` through `sh` because
the wrapper is tracked without executable permission. Maven's failure exit code
fails the job; no test failures are ignored. There are no deployment steps.

After reviewing, committing, and pushing the workflow on a feature branch, open
the repository's **Actions** tab and select **Backend CI**. Check the Java setup,
PostgreSQL service, and Maven test log. The Stage 1 suite should report 382 tests
with zero failures, errors, or skips. This preserves the original 208-test baseline
and adds scheduling and migration coverage.
Opening or updating a pull request targeting `main` should also show the check
on the PR's **Checks** tab. A failed test or failed database startup must leave
the check red. Repository Actions settings must allow the official checkout and
setup-java actions.

## Stage 1 scheduling tests and migrations

The same Java 21/Maven commands above discover all new test classes; CI's existing
`availability.test.jdbc-*` properties enable both availability and scheduling
integration tests. The existing workflow requires no changes.

| New test class | Scope | Tests |
|---|---|---:|
| `WeeklyScheduleServiceTest` | DRAFT defaults, Monday validation, duplicate races, lookup/list/detail | 18 |
| `ShiftServiceTest` | Assignment validation, availability coverage, overlap checks, updates, deletion, database-conflict mapping | 30 |
| `SchedulingControllerTest` | DTO/HTTP contracts, malformed requests, real JWT/security filter chain, ADMIN-only routes | 62 |
| `ShiftSchemaValidatorTest` | Fail-fast check for required native schema constraints | 2 |
| `SchedulingIntegrationTest` | Full HTTP/JWT/services/JPA/PostgreSQL CRUD and concurrency | 57 |
| `SchedulingMigrationTest` | Actual legacy expansion/backfill/finalization scripts in an isolated schema | 5 |

The complete enabled suite has **382 tests**: 287 unit/MVC tests and 95 database
integration invocations (33 availability, 57 scheduling, 5 migration). All existing
208 tests remain. Without the JDBC property, database tests are intentionally
skipped, including parameterized test templates.

The integration role needs permission to install PostgreSQL's bundled
`btree_gist` extension in the disposable test database. GitHub CI's PostgreSQL
service user already has the necessary privileges. No Maven dependencies were
added. `SchedulingTestDatabaseConfig` installs the same native shift-finalization
SQL after Hibernate creates fresh test tables and before the production schema
validator runs during context initialization, before the web server accepts
requests. The availability integration application uses this test-only
initializer too; its test cases are unchanged.

For application databases, Hibernate now uses `ddl-auto=validate` and does not
perform automatic migration. Apply the explicit additive/backfill/finalization
steps in [db/MIGRATION.md](db/MIGRATION.md) while the application is stopped.
The scripts keep legacy shift rows and their old weekday text; dates must be
explicitly supplied when legacy shifts exist. Never point test `create-drop`
configuration at an application database.

See [docs/STAGE1_API.md](docs/STAGE1_API.md) for routes, examples, statuses,
whole-second time precision, the half-open overlap rule, PostgreSQL exclusion
constraint concurrency protection, and Stage 1 limitations. Publishing, employee
visibility, overnight shifts, time-off integration, and deployment are not included.
