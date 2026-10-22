package com.farouk.employee_scheduler.integration;

import com.farouk.employee_scheduler.EmployeeSchedulerApplication;
import com.farouk.employee_scheduler.model.Availability;
import com.farouk.employee_scheduler.model.Employee;
import com.farouk.employee_scheduler.model.Role;
import com.farouk.employee_scheduler.model.User;
import com.farouk.employee_scheduler.repository.AvailabilityRepository;
import com.farouk.employee_scheduler.repository.EmployeeRepository;
import com.farouk.employee_scheduler.repository.UserRepository;
import com.farouk.employee_scheduler.security.JwtService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP, signed JWTs, services, JPA repositories and PostgreSQL; no application mocks. */
@EnabledIfSystemProperty(named = "availability.test.jdbc-url", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AvailabilityIntegrationTest {
    private static final String URL = "/api/availability/me";
    private ConfigurableApplicationContext context;
    private HttpClient client;
    private final ObjectMapper json = new ObjectMapper();
    private AvailabilityRepository availabilityRepository;
    private EmployeeRepository employeeRepository;
    private UserRepository userRepository;
    private JwtService jwt;
    private Employee alice;
    private Employee bob;
    private String aliceToken;
    private String bobToken;
    private String baseUrl;

    @BeforeAll
    void startApplication() {
        String jdbcUrl = System.getProperty("availability.test.jdbc-url");
        // create-drop must never be pointed at the application database.
        assertTrue(jdbcUrl.matches("jdbc:postgresql://[^/]+/availability_test(?:\\?.*)?"),
                "Use a dedicated database named availability_test; integration tests recreate its tables");
        context = new SpringApplicationBuilder(EmployeeSchedulerApplication.class).run(
                "--server.port=0",
                "--spring.datasource.url=" + jdbcUrl,
                "--spring.datasource.username=" + System.getProperty("availability.test.jdbc-user", "postgres"),
                "--spring.datasource.password=" + System.getProperty("availability.test.jdbc-password", ""),
                "--spring.datasource.hikari.maximum-pool-size=4",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.jpa.show-sql=false",
                "--spring.jpa.open-in-view=false",
                "--app.bootstrap.admin.enabled=false",
                "--app.environment=test",
                "--jwt.secret=availability-integration-test-only-key-0123456789",
                "--jwt.expiration-ms=3600000"
        );
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        baseUrl = "http://127.0.0.1:" + port;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        availabilityRepository = context.getBean(AvailabilityRepository.class);
        employeeRepository = context.getBean(EmployeeRepository.class);
        userRepository = context.getBean(UserRepository.class);
        jwt = context.getBean(JwtService.class);
    }

    @BeforeEach
    void createEmployees() {
        availabilityRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
        alice = employee("alice", "EMP001");
        bob = employee("bob", "EMP002");
        aliceToken = token(alice);
        bobToken = token(bob);
    }

    @AfterAll
    void stopApplication() {
        if (client != null) client.close();
        if (context != null) context.close();
    }

    @Test
    void completeCrudPersistsUpdatesAndDeletion() throws Exception {
        var created = send("POST", URL, body("MONDAY", "09:00", "17:00"), aliceToken);
        assertEquals(201, created.statusCode());
        var original = json.readTree(created.body());
        assertNotNull(original.get("id"));
        assertEquals(alice.getId().toString(), original.get("employeeId").asString());
        assertEquals(5, original.size());

        var listed = send("GET", URL, null, aliceToken);
        assertEquals(200, listed.statusCode());
        assertEquals(1, json.readTree(listed.body()).size());

        var updated = send("PUT", URL + "/MONDAY", body("MONDAY", "10:00", "18:00"), aliceToken);
        assertEquals(200, updated.statusCode());
        assertEquals(original.get("id"), json.readTree(updated.body()).get("id"));
        var persisted = availabilityRepository.findByEmployee_IdAndDayOfWeek(alice.getId(), DayOfWeek.MONDAY).orElseThrow();
        assertEquals(LocalTime.of(10, 0), persisted.getStartTime());
        assertEquals(LocalTime.of(18, 0), persisted.getEndTime());

        var deleted = send("DELETE", URL + "/MONDAY", null, aliceToken);
        assertEquals(204, deleted.statusCode());
        assertTrue(deleted.body().isEmpty());
        assertTrue(availabilityRepository.findByEmployeeId(alice.getId()).isEmpty());
        assertTrue(employeeRepository.existsById(alice.getId()));
        assertTrue(userRepository.existsById(alice.getUser().getId()));
    }

    @Test
    void listCannotBeRedirectedToAnotherEmployeeByQueryParameters() throws Exception {
        add(bob, DayOfWeek.MONDAY);
        var result = send("GET", URL + "?employeeId=" + bob.getId() + "&userId=" + bob.getUser().getId(), null, aliceToken);
        assertEquals(200, result.statusCode());
        assertEquals(0, json.readTree(result.body()).size());
        assertEquals(1, json.readTree(send("GET", URL, null, bobToken).body()).size());
    }

    @Test
    void bodyCannotAssignAvailabilityToAnotherEmployee() throws Exception {
        String spoofed = body("MONDAY", "09:00", "17:00").replace("}", ",\"employeeId\":\"" + bob.getId()
                + "\",\"userId\":\"" + bob.getUser().getId() + "\"}");
        var result = send("POST", URL, spoofed, aliceToken);
        assertEquals(201, result.statusCode());
        assertEquals(alice.getId().toString(), json.readTree(result.body()).get("employeeId").asString());
        assertTrue(availabilityRepository.findByEmployeeId(bob.getId()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void anotherEmployeesDayIsNotVisibleForMutation(String method) throws Exception {
        var other = add(bob, DayOfWeek.MONDAY);
        assertEquals(404, send(method, URL + "/MONDAY", body("MONDAY", "10:00", "18:00"), aliceToken).statusCode());
        assertTrue(availabilityRepository.existsById(other.getId()));
        assertEquals(LocalTime.of(9, 0), availabilityRepository.findById(other.getId()).orElseThrow().getStartTime());
    }

    @Test
    void sameDayUpdatesAndDeletesAreScopedToTheEmployee() throws Exception {
        var own = add(alice, DayOfWeek.MONDAY);
        var other = add(bob, DayOfWeek.MONDAY);
        assertEquals(200, send("PUT", URL + "/MONDAY", body("MONDAY", "10:00", "18:00"), aliceToken).statusCode());
        assertEquals(LocalTime.of(9, 0), availabilityRepository.findById(other.getId()).orElseThrow().getStartTime());
        assertEquals(204, send("DELETE", URL + "/MONDAY", null, aliceToken).statusCode());
        assertFalse(availabilityRepository.existsById(own.getId()));
        assertTrue(availabilityRepository.existsById(other.getId()));
    }

    @Test
    void employeeCanHaveOneIntervalOnEachOfSevenDays() throws Exception {
        for (var day : DayOfWeek.values()) {
            assertEquals(201, send("POST", URL, body(day.name(), "09:00", "17:00"), aliceToken).statusCode());
        }
        assertEquals(7, availabilityRepository.findByEmployeeId(alice.getId()).size());
        assertEquals(409, send("POST", URL, body("MONDAY", "18:00", "19:00"), aliceToken).statusCode());
        assertEquals(201, send("POST", URL, body("MONDAY", "09:00", "17:00"), bobToken).statusCode());
    }

    @Test
    void databaseRejectsDuplicateEmployeeDayEvenWhenServiceIsBypassed() {
        add(alice, DayOfWeek.MONDAY);
        var failure = assertThrows(DataIntegrityViolationException.class, () -> add(alice, DayOfWeek.MONDAY));
        assertTrue(hasSqlState(failure, "23505"));
        assertEquals(1, availabilityRepository.findByEmployeeId(alice.getId()).size());
    }

    @Test
    void concurrentCreatesReturnOne201AndConflictsWithoutServerErrors() throws Exception {
        int requests = 8;
        var ready = new CountDownLatch(requests);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(requests)) {
            var results = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < requests; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(15, TimeUnit.SECONDS));
                    return send("POST", URL, body("MONDAY", "09:00", "17:00"), aliceToken).statusCode();
                }));
            }
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();
            var statuses = new ArrayList<Integer>();
            for (var result : results) statuses.add(result.get(30, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(status -> status == 201).count());
            assertEquals(requests - 1, statuses.stream().filter(status -> status == 409).count());
        }
        assertEquals(1, availabilityRepository.findByEmployeeId(alice.getId()).size());
    }

    @ParameterizedTest
    @CsvSource({"POST,09:00,09:00", "POST,17:00,09:00", "POST,23:00,01:00",
            "PUT,09:00,09:00", "PUT,17:00,09:00", "PUT,23:00,01:00"})
    void invalidTimeRangesReturn400AndDoNotChangeData(String method, String start, String end) throws Exception {
        var existing = add(alice, DayOfWeek.MONDAY);
        String day = method.equals("POST") ? "TUESDAY" : "MONDAY";
        String url = method.equals("POST") ? URL : URL + "/MONDAY";
        assertEquals(400, send(method, url, body(day, start, end), aliceToken).statusCode());
        assertEquals(1, availabilityRepository.count());
        assertEquals(LocalTime.of(9, 0), availabilityRepository.findById(existing.getId()).orElseThrow().getStartTime());
    }

    @Test
    void midnightAndOneSecondIntervalsAreValid() throws Exception {
        assertEquals(201, send("POST", URL, body("MONDAY", "00:00:00", "00:00:01"), aliceToken).statusCode());
        assertEquals(201, send("POST", URL, body("SUNDAY", "23:59:58", "23:59:59"), aliceToken).statusCode());
    }

    @Test
    void mismatchedPutDayReturns400AndPreservesBothDays() throws Exception {
        add(alice, DayOfWeek.MONDAY);
        add(alice, DayOfWeek.TUESDAY);
        assertEquals(400, send("PUT", URL + "/MONDAY", body("TUESDAY", "10:00", "18:00"), aliceToken).statusCode());
        for (var availability : availabilityRepository.findByEmployeeId(alice.getId())) {
            assertEquals(LocalTime.of(9, 0), availability.getStartTime());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void missingRecordReturns404(String method) throws Exception {
        assertEquals(404, send(method, URL + "/MONDAY", body("MONDAY", "09:00", "17:00"), aliceToken).statusCode());
        assertEquals(0, availabilityRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "GET", "PUT", "DELETE"})
    void anonymousRequestsReturn401(String method) throws Exception {
        assertEquals(401, send(method, mutationUrl(method), body("MONDAY", "09:00", "17:00"), null).statusCode());
        assertEquals(0, availabilityRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "GET", "PUT", "DELETE"})
    void adminsCannotUseEmployeeOnlyAvailabilityRoutes(String method) throws Exception {
        var admin = userRepository.saveAndFlush(new User("admin@test.invalid", "unused", "Admin", "Test", Role.ADMIN));
        String token = jwt.generate(admin.getId().toString(), Map.of("role", "ADMIN"));
        assertEquals(403, send(method, mutationUrl(method), body("MONDAY", "09:00", "17:00"), token).statusCode());
        assertEquals(0, availabilityRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "GET", "PUT", "DELETE"})
    void employeeTokenWithoutProfileReturns404(String method) throws Exception {
        String token = jwt.generate(UUID.randomUUID().toString(), Map.of("role", "EMPLOYEE"));
        assertEquals(404, send(method, mutationUrl(method), body("MONDAY", "09:00", "17:00"), token).statusCode());
    }

    @Test
    void malformedSignedSubjectReturns401InsteadOf500() throws Exception {
        String token = jwt.generate("not-a-uuid", Map.of("role", "EMPLOYEE"));
        assertEquals(401, send("GET", URL, null, token).statusCode());
    }

    @Test
    void invalidBearerTokenReturns401ThroughRealFilterChain() throws Exception {
        assertEquals(401, send("GET", URL, null, "invalid-token").statusCode());
    }

    private String mutationUrl(String method) {
        return method.equals("PUT") || method.equals("DELETE") ? URL + "/MONDAY" : URL;
    }

    private Employee employee(String name, String number) {
        var user = userRepository.saveAndFlush(new User(name + "@test.invalid", "unused", name, "Test", Role.EMPLOYEE));
        return employeeRepository.saveAndFlush(new Employee(LocalDate.of(2026, 1, 1), null, number, user, null));
    }

    private String token(Employee employee) {
        return jwt.generate(employee.getUser().getId().toString(), Map.of("role", "EMPLOYEE"));
    }

    private Availability add(Employee employee, DayOfWeek day) {
        return availabilityRepository.saveAndFlush(new Availability(day, LocalTime.of(9, 0), LocalTime.of(17, 0), employee));
    }

    private String body(String day, String start, String end) {
        return "{\"dayOfWeek\":\"" + day + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
    }

    private HttpResponse<String> send(String method, String url, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + url)).timeout(Duration.ofSeconds(20));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private boolean hasSqlState(Throwable exception, String sqlState) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sqlState.equals(sql.getSQLState())) return true;
        }
        return false;
    }
}
