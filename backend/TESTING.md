# Backend tests

Run the complete suite from `backend/` with Java 21:

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

The generated full-application `contextLoads` test was replaced by the MVC tests
because it required PostgreSQL and runtime JWT configuration. JUnit Jupiter is
pinned to 5.14.4 in the POM to honor the project's JUnit 5 requirement; Spring Boot
4.1.1 otherwise manages JUnit 6.
The MVC context is assembled directly because Spring 7.1's JUnit extension uses
JUnit 6 APIs. This keeps every test on JUnit 5 while preserving real MVC request
handling, validation, and security filters.

Surefire loads Mockito as a Java agent at JVM startup. This avoids dynamic
Java-agent attachment during test startup on Java 21.

These tests verify behavior and repository interactions, not SQL constraints,
JPA dirty checking, or transaction rollback. Before merging, run the suite in CI
with Java 21. A separate PostgreSQL integration suite would be useful for account
and employee transaction atomicity, update persistence, concurrent uniqueness
conflicts, and deletion when an employee has related scheduling records.
