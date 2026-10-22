package com.farouk.employee_scheduler.integration;

import com.farouk.employee_scheduler.EmployeeSchedulerApplication;
import com.farouk.employee_scheduler.model.*;
import com.farouk.employee_scheduler.repository.*;
import com.farouk.employee_scheduler.security.JwtService;
import com.farouk.employee_scheduler.support.SchedulingTestDatabaseConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "availability.test.jdbc-url", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchedulingIntegrationTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
    private ConfigurableApplicationContext context;
    private HttpClient client;
    private final ObjectMapper json = new ObjectMapper();
    private ShiftRepository shifts;
    private WeeklyScheduleRepository schedules;
    private EmployeeRepository employees;
    private UserRepository users;
    private AvailabilityRepository availability;
    private JwtService jwt;
    private WeeklySchedule schedule;
    private Employee alice;
    private Employee bob;
    private String adminToken;
    private String employeeToken;
    private String baseUrl;

    @BeforeAll
    void start() {
        String url = System.getProperty("availability.test.jdbc-url");
        assertTrue(url.matches("jdbc:postgresql://[^/]+/availability_test(?:\\?.*)?"), "Use only disposable availability_test");
        context = new SpringApplicationBuilder(EmployeeSchedulerApplication.class, SchedulingTestDatabaseConfig.class).run(
                "--server.port=0", "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + System.getProperty("availability.test.jdbc-user", "postgres"),
                "--spring.datasource.password=" + System.getProperty("availability.test.jdbc-password", ""),
                "--spring.datasource.hikari.maximum-pool-size=6", "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.jpa.show-sql=false", "--spring.jpa.open-in-view=false", "--app.bootstrap.admin.enabled=false",
                "--app.environment=test", "--jwt.secret=scheduling-integration-tests-only-secret-0123456789", "--jwt.expiration-ms=3600000");
        baseUrl = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        shifts = context.getBean(ShiftRepository.class); schedules = context.getBean(WeeklyScheduleRepository.class);
        employees = context.getBean(EmployeeRepository.class); users = context.getBean(UserRepository.class);
        availability = context.getBean(AvailabilityRepository.class); jwt = context.getBean(JwtService.class);
    }

    @BeforeEach
    void fixtures() {
        shifts.deleteAll(); schedules.deleteAll(); availability.deleteAll(); employees.deleteAll(); users.deleteAll();
        alice = employee("alice", "EMP001"); bob = employee("bob", "EMP002");
        for (var employee : List.of(alice, bob)) {
            for (var day : DayOfWeek.values()) availability.saveAndFlush(new Availability(day, LocalTime.of(9, 0), LocalTime.of(17, 0), employee));
        }
        schedule = schedules.saveAndFlush(new WeeklySchedule(MONDAY));
        var admin = users.saveAndFlush(new User("admin@test.invalid", "unused", "Admin", "Test", Role.ADMIN));
        adminToken = jwt.generate(admin.getId().toString(), Map.of("role", "ADMIN"));
        employeeToken = jwt.generate(alice.getUser().getId().toString(), Map.of("role", "EMPLOYEE"));
    }

    @AfterAll
    void stop() {
        if (client != null) client.close();
        if (context != null) context.close();
    }

    @Test
    void createsDraftAndRetrievesScheduleListAndDetail() throws Exception {
        var response = send("POST", "/api/admin/schedules", "{\"weekStartDate\":\"2026-11-02\"}", adminToken);
        status(201, response);
        var created = json.readTree(response.body());
        assertEquals("DRAFT", created.get("status").asString()); assertNotNull(created.get("createdAt"));
        assertEquals(4, created.size());
        var listed = send("GET", "/api/admin/schedules", null, adminToken); status(200, listed);
        assertEquals(2, json.readTree(listed.body()).size());
        assertEquals("2026-11-02", json.readTree(listed.body()).get(0).get("weekStartDate").asString());
        var detail = send("GET", "/api/admin/schedules/" + created.get("id").asString(), null, adminToken); status(200, detail);
        assertEquals(0, json.readTree(detail.body()).get("shifts").size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-27", "2026-10-28", "2026-10-29", "2026-10-30", "2026-10-31", "2026-11-01"})
    void nonMondayScheduleIs400(String date) throws Exception {
        status(400, send("POST", "/api/admin/schedules", "{\"weekStartDate\":\"" + date + "\"}", adminToken));
        assertEquals(1, schedules.count());
    }

    @Test
    void duplicateMondayIs409() throws Exception {
        status(409, send("POST", "/api/admin/schedules", "{\"weekStartDate\":\"2026-10-26\"}", adminToken));
        assertEquals(1, schedules.count());
    }

    @Test
    void concurrentScheduleCreationHasExactlyOneWinner() throws Exception {
        var statuses = concurrent(8, () -> send("POST", "/api/admin/schedules", "{\"weekStartDate\":\"2026-11-02\"}", adminToken).statusCode());
        assertOneWinner(statuses, 201, 409);
        assertEquals(2, schedules.count());
    }

    @Test
    void completeShiftCrudIncludesAssignmentsInScheduleDetail() throws Exception {
        JsonNode created = create(alice, MONDAY, "10:00", "16:00");
        UUID id = UUID.fromString(created.get("id").asString());
        assertEquals(schedule.getId().toString(), created.get("scheduleId").asString());
        assertEquals(alice.getId().toString(), created.get("employeeId").asString());
        var detail = send("GET", "/api/admin/schedules/" + schedule.getId(), null, adminToken); status(200, detail);
        assertEquals(1, json.readTree(detail.body()).get("shifts").size());
        status(200, send("PUT", "/api/admin/shifts/" + id, body(bob.getId(), MONDAY.plusDays(6), "09:00", "12:00"), adminToken));
        Shift persisted = shifts.findById(id).orElseThrow();
        assertEquals(bob.getId(), persisted.getEmployee().getId());
        assertEquals(MONDAY.plusDays(6), persisted.getShiftDate());
        assertEquals(LocalTime.NOON, persisted.getEndTime());
        assertEquals(schedule.getId(), persisted.getWeeklySchedule().getId());
        var deleted = send("DELETE", "/api/admin/shifts/" + id, null, adminToken); status(204, deleted);
        assertEquals("", deleted.body()); assertFalse(shifts.existsById(id));
        assertTrue(employees.existsById(bob.getId())); assertTrue(schedules.existsById(schedule.getId()));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 7})
    void shiftOutsideWeekIs400(int offset) throws Exception {
        status(400, send("POST", createUrl(), body(alice.getId(), MONDAY.plusDays(offset), "10:00", "16:00"), adminToken));
        assertEquals(0, shifts.count());
    }

    @Test
    void mondayAndSundayBothBelongToWeek() throws Exception {
        create(alice, MONDAY, "09:00", "17:00"); create(alice, MONDAY.plusDays(6), "09:00", "17:00");
        assertEquals(2, shifts.count());
    }

    @Test
    void missingEmployeeAndScheduleAndShiftAre404() throws Exception {
        status(404, send("POST", createUrl(), body(UUID.randomUUID(), MONDAY, "10:00", "16:00"), adminToken));
        status(404, send("POST", "/api/admin/schedules/" + UUID.randomUUID() + "/shifts", body(alice.getId(), MONDAY, "10:00", "16:00"), adminToken));
        status(404, send("GET", "/api/admin/schedules/" + UUID.randomUUID(), null, adminToken));
        status(404, send("PUT", "/api/admin/shifts/" + UUID.randomUUID(), body(alice.getId(), MONDAY, "10:00", "16:00"), adminToken));
        status(404, send("DELETE", "/api/admin/shifts/" + UUID.randomUUID(), null, adminToken));
    }

    @Test
    void missingAvailabilityIs409() throws Exception {
        availability.delete(availability.findByEmployee_IdAndDayOfWeek(alice.getId(), DayOfWeek.MONDAY).orElseThrow());
        status(409, send("POST", createUrl(), body(alice.getId(), MONDAY, "10:00", "16:00"), adminToken));
        assertEquals(0, shifts.count());
    }

    @ParameterizedTest
    @CsvSource({"08:00,12:00", "16:00,18:00", "17:00,20:00", "06:00,08:00", "08:00,18:00"})
    void uncoveredShiftIs409(String start, String end) throws Exception {
        status(409, send("POST", createUrl(), body(alice.getId(), MONDAY, start, end), adminToken));
        assertEquals(0, shifts.count());
    }

    @ParameterizedTest
    @CsvSource({"09:00,09:00", "17:00,09:00", "23:00,01:00", "09:00:00.1,10:00", "09:00,10:00:00.1"})
    void invalidRangesAre400(String start, String end) throws Exception {
        status(400, send("POST", createUrl(), body(alice.getId(), MONDAY, start, end), adminToken));
        assertEquals(0, shifts.count());
    }

    @ParameterizedTest
    @CsvSource({"09:00,12:00", "10:00,11:00", "08:00,13:00", "11:00,14:00", "09:00,17:00"})
    void overlapIs409(String start, String end) throws Exception {
        create(alice, MONDAY, "09:00", "12:00");
        status(409, send("POST", createUrl(), body(alice.getId(), MONDAY, start, end), adminToken));
        assertEquals(1, shifts.count());
    }

    @Test
    void adjacentShiftsAndOtherEmployeesAtSameTimeArePermitted() throws Exception {
        create(alice, MONDAY, "09:00", "12:00"); create(alice, MONDAY, "12:00", "17:00");
        create(bob, MONDAY, "09:00", "17:00");
        assertEquals(3, shifts.count());
    }

    @Test
    void sameHoursOnDifferentDatesOrWeeksAreIndependent() throws Exception {
        create(alice, MONDAY, "09:00", "12:00"); create(alice, MONDAY.plusDays(1), "09:00", "12:00");
        var next = schedules.saveAndFlush(new WeeklySchedule(MONDAY.plusWeeks(1)));
        status(201, send("POST", "/api/admin/schedules/" + next.getId() + "/shifts", body(alice.getId(), MONDAY.plusWeeks(1), "09:00", "12:00"), adminToken));
        assertEquals(3, shifts.count());
    }

    @Test
    void updateExcludesItselfAndAllowsUnchangedTimes() throws Exception {
        var id = create(alice, MONDAY, "09:00", "12:00").get("id").asString();
        status(200, send("PUT", "/api/admin/shifts/" + id, body(alice.getId(), MONDAY, "09:00", "12:00"), adminToken));
        assertEquals(1, shifts.count());
    }

    @ParameterizedTest
    @CsvSource({"2026-11-02,10:00,11:00,400", "2026-10-26,12:00,12:00,400", "2026-10-26,23:00,01:00,400",
            "2026-10-26,08:00,12:00,409", "2026-10-26,16:00,18:00,409", "2026-10-26,11:00,14:00,409"})
    void invalidUpdateRollsBackAllAssignmentChanges(String date, String start, String end, int expected) throws Exception {
        var id = UUID.fromString(create(alice, MONDAY, "09:00", "12:00").get("id").asString());
        create(alice, MONDAY, "12:00", "17:00");
        status(expected, send("PUT", "/api/admin/shifts/" + id, body(alice.getId(), LocalDate.parse(date), start, end), adminToken));
        Shift persisted = shifts.findById(id).orElseThrow();
        assertEquals(MONDAY, persisted.getShiftDate()); assertEquals(LocalTime.of(9, 0), persisted.getStartTime());
        assertEquals(LocalTime.NOON, persisted.getEndTime()); assertEquals(2, shifts.count());
    }

    @Test
    void reassigningToAlreadyBusyEmployeeIs409() throws Exception {
        var id = create(alice, MONDAY, "09:00", "12:00").get("id").asString();
        create(bob, MONDAY, "09:00", "12:00");
        status(409, send("PUT", "/api/admin/shifts/" + id, body(bob.getId(), MONDAY, "10:00", "11:00"), adminToken));
        assertEquals(alice.getId(), shifts.findById(UUID.fromString(id)).orElseThrow().getEmployee().getId());
    }

    @Test
    void updateToEmployeeWithoutAvailabilityIs409() throws Exception {
        var id = create(alice, MONDAY, "09:00", "12:00").get("id").asString();
        availability.delete(availability.findByEmployee_IdAndDayOfWeek(bob.getId(), DayOfWeek.MONDAY).orElseThrow());
        status(409, send("PUT", "/api/admin/shifts/" + id, body(bob.getId(), MONDAY, "10:00", "11:00"), adminToken));
    }

    @Test
    void updateToMissingEmployeeIs404AndPreservesAssignment() throws Exception {
        var id = UUID.fromString(create(alice, MONDAY, "09:00", "12:00").get("id").asString());
        status(404, send("PUT", "/api/admin/shifts/" + id, body(UUID.randomUUID(), MONDAY, "10:00", "16:00"), adminToken));
        assertEquals(alice.getId(), shifts.findById(id).orElseThrow().getEmployee().getId());
        assertEquals(LocalTime.of(9, 0), shifts.findById(id).orElseThrow().getStartTime());
    }

    @Test
    void concurrentOverlappingRequestsHaveOneWinner() throws Exception {
        var statuses = concurrent(8, () -> send("POST", createUrl(), body(alice.getId(), MONDAY, "10:00", "16:00"), adminToken).statusCode());
        assertOneWinner(statuses, 201, 409); assertEquals(1, shifts.count());
    }

    @Test
    void concurrentUpdatesCannotCreateOverlaps() throws Exception {
        String first = create(alice, MONDAY, "09:00", "10:00").get("id").asString();
        String second = create(alice, MONDAY, "16:00", "17:00").get("id").asString();
        var ids = new java.util.concurrent.atomic.AtomicInteger();
        var statuses = concurrent(2, () -> send("PUT", "/api/admin/shifts/" + (ids.getAndIncrement() == 0 ? first : second),
                body(alice.getId(), MONDAY, "11:00", "15:00"), adminToken).statusCode());
        assertOneWinner(statuses, 200, 409);
    }

    @Test
    void nativeExclusionProtectsWritesThatBypassAllServiceChecks() throws Exception {
        var results = concurrent(2, () -> {
            try {
                shifts.saveAndFlush(new Shift(schedule, alice, MONDAY, LocalTime.of(10, 0), LocalTime.of(16, 0)));
                return "saved";
            } catch (RuntimeException exception) {
                for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                    if (cause instanceof SQLException sql) return sql.getSQLState();
                }
                throw exception;
            }
        });
        assertEquals(1, results.stream().filter("saved"::equals).count(), results.toString());
        // Simultaneous GiST checks can either reject the exclusion or cause
        // PostgreSQL to abort one transaction as a deadlock victim. Neither commits.
        assertEquals(1, results.stream().filter(code -> code.equals("23P01") || code.equals("40P01")).count(), results.toString());
        assertEquals(1, shifts.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE_SCHEDULE", "LIST", "DETAIL", "CREATE_SHIFT", "UPDATE_SHIFT", "DELETE_SHIFT"})
    void employeeCannotAccessAdminRoutes(String route) throws Exception {
        status(403, route(route, employeeToken));
        assertEquals(0, shifts.count()); assertEquals(1, schedules.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE_SCHEDULE", "LIST", "DETAIL", "CREATE_SHIFT", "UPDATE_SHIFT", "DELETE_SHIFT"})
    void unauthenticatedRequestsAre401(String route) throws Exception { status(401, route(route, null)); }

    private HttpResponse<String> route(String route, String token) throws Exception {
        return switch (route) {
            case "CREATE_SCHEDULE" -> send("POST", "/api/admin/schedules", "{\"weekStartDate\":\"2026-11-02\"}", token);
            case "LIST" -> send("GET", "/api/admin/schedules", null, token);
            case "DETAIL" -> send("GET", "/api/admin/schedules/" + schedule.getId(), null, token);
            case "CREATE_SHIFT" -> send("POST", createUrl(), body(alice.getId(), MONDAY, "10:00", "16:00"), token);
            case "UPDATE_SHIFT" -> send("PUT", "/api/admin/shifts/" + UUID.randomUUID(), body(alice.getId(), MONDAY, "10:00", "16:00"), token);
            default -> send("DELETE", "/api/admin/shifts/" + UUID.randomUUID(), null, token);
        };
    }

    private Employee employee(String name, String number) {
        var user = users.saveAndFlush(new User(name + "@test.invalid", "unused", name, "Test", Role.EMPLOYEE));
        return employees.saveAndFlush(new Employee(MONDAY, null, number, user, null));
    }

    private String createUrl() { return "/api/admin/schedules/" + schedule.getId() + "/shifts"; }

    private JsonNode create(Employee employee, LocalDate date, String start, String end) throws Exception {
        var response = send("POST", createUrl(), body(employee.getId(), date, start, end), adminToken);
        status(201, response); return json.readTree(response.body());
    }

    private String body(UUID employee, LocalDate date, String start, String end) {
        return "{\"employeeId\":\"" + employee + "\",\"shiftDate\":\"" + date + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(25));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void status(int expected, HttpResponse<String> response) { assertEquals(expected, response.statusCode(), response.body()); }

    private <T> List<T> concurrent(int count, Callable<T> work) throws Exception {
        var ready = new CountDownLatch(count); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            var futures = new ArrayList<Future<T>>();
            for (int i = 0; i < count; i++) futures.add(executor.submit(() -> {
                ready.countDown(); assertTrue(start.await(15, TimeUnit.SECONDS)); return work.call();
            }));
            assertTrue(ready.await(15, TimeUnit.SECONDS)); start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) results.add(future.get(40, TimeUnit.SECONDS));
            return results;
        }
    }

    private void assertOneWinner(List<Integer> results, int success, int conflict) {
        assertEquals(1, results.stream().filter(code -> code == success).count(), results.toString());
        assertEquals(results.size() - 1, results.stream().filter(code -> code == conflict).count(), results.toString());
    }
}
