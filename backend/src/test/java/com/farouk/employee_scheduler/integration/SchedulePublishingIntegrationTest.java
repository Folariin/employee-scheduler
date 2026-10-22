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
class SchedulePublishingIntegrationTest {
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

    @Test void mixedWeeksExcludeDraftsAndKeepOwnPublishedWeeksInCalendarOrder() throws Exception {
        create(alice,MONDAY,"09:00","12:00"); publish();
        WeeklySchedule later = schedules.saveAndFlush(new WeeklySchedule(MONDAY.plusDays(14)));
        shifts.saveAndFlush(new Shift(later,alice,MONDAY.plusDays(14),LocalTime.of(9,0),LocalTime.NOON));
        shifts.saveAndFlush(new Shift(later,bob,MONDAY.plusDays(14),LocalTime.of(9,0),LocalTime.NOON));
        context.getBean(com.farouk.employee_scheduler.service.WeeklyScheduleService.class).publish(later.getId());
        WeeklySchedule draft = schedules.saveAndFlush(new WeeklySchedule(MONDAY.plusDays(7)));
        shifts.saveAndFlush(new Shift(draft,alice,MONDAY.plusDays(7),LocalTime.of(9,0),LocalTime.NOON));
        var weeks = own("/api/schedules/me",employeeToken);
        assertEquals(2,weeks.size()); assertEquals(schedule.getId().toString(),weeks.get(0).get("id").asString());
        assertEquals(later.getId().toString(),weeks.get(1).get("id").asString());
        assertEquals(1,weeks.get(1).get("shifts").size());
        assertEquals(2,own("/api/shifts/me",employeeToken).size());
        var filtered = own("/api/schedules/me?startDate=2026-11-02",employeeToken);
        assertEquals(1,filtered.size()); assertEquals(later.getId().toString(),filtered.get(0).get("id").asString());
    }

    private String publishUrl() { return "/api/admin/schedules/" + schedule.getId() + "/publish"; }
    private void publish() throws Exception { status(200, send("POST", publishUrl(), null, adminToken)); }
    private JsonNode own(String path, String token) throws Exception {
        var response = send("GET", path, null, token); status(200, response); return json.readTree(response.body());
    }
    private String token(Employee employee) { return jwt.generate(employee.getUser().getId().toString(), Map.of("role", "EMPLOYEE")); }

    @Test void publishingPreservesAllAssignmentsAndAdminVisibility() throws Exception {
        String first = create(alice, MONDAY, "09:00", "12:00").get("id").asString();
        String second = create(bob, MONDAY, "09:00", "12:00").get("id").asString();
        Instant createdAt = schedules.findById(schedule.getId()).orElseThrow().getCreatedAt();
        var response = send("POST", publishUrl(), null, adminToken); status(200, response);
        assertEquals("PUBLISHED", json.readTree(response.body()).get("status").asString());
        assertEquals(createdAt, schedules.findById(schedule.getId()).orElseThrow().getCreatedAt());
        assertTrue(shifts.existsById(UUID.fromString(first))); assertTrue(shifts.existsById(UUID.fromString(second)));
        var detail = own("/api/admin/schedules/" + schedule.getId(), adminToken);
        assertEquals(2, detail.get("shifts").size()); assertEquals("PUBLISHED", detail.get("status").asString());
        assertEquals("PUBLISHED", own("/api/admin/schedules", adminToken).get(0).get("status").asString());
    }
    @Test void missingPublicationIs404AndRepeatedPublicationIs409() throws Exception {
        status(404, send("POST", "/api/admin/schedules/" + UUID.randomUUID() + "/publish", null, adminToken));
        publish(); status(409, send("POST", publishUrl(), null, adminToken));
        assertEquals(ScheduleStatus.PUBLISHED, schedules.findById(schedule.getId()).orElseThrow().getStatus());
    }
    @ParameterizedTest @ValueSource(strings = {"create", "update", "reassign", "delete"})
    void publicationFreezesAllShiftMutations(String operation) throws Exception {
        String id = create(alice, MONDAY, "09:00", "12:00").get("id").asString(); publish();
        var response = switch (operation) {
            case "create" -> send("POST", createUrl(), body(alice.getId(), MONDAY, "13:00", "16:00"), adminToken);
            case "update" -> send("PUT", "/api/admin/shifts/" + id, body(alice.getId(), MONDAY, "10:00", "16:00"), adminToken);
            case "reassign" -> send("PUT", "/api/admin/shifts/" + id, body(bob.getId(), MONDAY, "09:00", "12:00"), adminToken);
            default -> send("DELETE", "/api/admin/shifts/" + id, null, adminToken);
        };
        status(409, response); assertEquals(1, shifts.count());
        Shift unchanged = shifts.findById(UUID.fromString(id)).orElseThrow();
        assertEquals(alice.getId(), unchanged.getEmployee().getId());
        assertEquals(LocalTime.of(9,0), unchanged.getStartTime()); assertEquals(LocalTime.NOON, unchanged.getEndTime());
    }
    @ParameterizedTest @ValueSource(strings = {"/api/shifts/me", "/api/schedules/me"})
    void draftHiddenAndPublishedAssignmentsIsolatedForEachEmployee(String path) throws Exception {
        String aliceId = create(alice, MONDAY, "09:00", "12:00").get("id").asString();
        String bobId = create(bob, MONDAY, "13:00", "16:00").get("id").asString();
        assertEquals(0, own(path, employeeToken).size()); assertEquals(0, own(path, token(bob)).size());
        publish();
        var mine = own(path + "?employeeId=" + bob.getId(), employeeToken);
        var theirs = own(path, token(bob));
        assertEquals(1, mine.size()); assertEquals(1, theirs.size());
        assertTrue(mine.toString().contains(aliceId)); assertFalse(mine.toString().contains(bobId));
        assertTrue(theirs.toString().contains(bobId)); assertFalse(theirs.toString().contains(aliceId));
        assertFalse(mine.toString().contains("@test.invalid"));
        if (path.contains("schedules")) {
            assertEquals("PUBLISHED", mine.get(0).get("status").asString());
            assertEquals(1, mine.get(0).get("shifts").size());
        }
    }
    @ParameterizedTest @ValueSource(strings = {"/api/shifts/me", "/api/schedules/me"})
    void emptyPublishedWeeksAndUnassignedEmployeesReturnEmptyArrays(String path) throws Exception {
        publish(); assertEquals(0, own(path, employeeToken).size());
        WeeklySchedule other = schedules.saveAndFlush(new WeeklySchedule(MONDAY.plusDays(7)));
        shifts.saveAndFlush(new Shift(other,bob,MONDAY.plusDays(7),LocalTime.of(9,0),LocalTime.NOON));
        context.getBean(com.farouk.employee_scheduler.service.WeeklyScheduleService.class).publish(other.getId());
        assertEquals(0, own(path, employeeToken).size()); assertEquals(1, own(path, token(bob)).size());
    }
    @ParameterizedTest @ValueSource(strings = {"/api/shifts/me", "/api/schedules/me"})
    void dateFiltersAreInclusiveOrderedAndCanBeOpenEnded(String path) throws Exception {
        create(alice, MONDAY.plusDays(2), "09:00", "12:00");
        String early = create(alice, MONDAY, "09:00", "12:00").get("id").asString();
        String late = create(alice, MONDAY, "13:00", "16:00").get("id").asString(); publish();
        JsonNode all = own(path,employeeToken);
        JsonNode assignments = path.contains("schedules") ? all.get(0).get("shifts") : all;
        assertEquals(3, assignments.size()); assertEquals(early, assignments.get(0).get("id").asString());
        assertEquals(late, assignments.get(1).get("id").asString());
        JsonNode filtered = own(path + "?startDate=2026-10-26&endDate=2026-10-26",employeeToken);
        assertEquals(2, (path.contains("schedules") ? filtered.get(0).get("shifts") : filtered).size());
        filtered = own(path + "?startDate=2026-10-27",employeeToken);
        assertEquals(1, (path.contains("schedules") ? filtered.get(0).get("shifts") : filtered).size());
        filtered = own(path + "?endDate=2026-10-26",employeeToken);
        assertEquals(2, (path.contains("schedules") ? filtered.get(0).get("shifts") : filtered).size());
        assertEquals(0, own(path + "?startDate=2026-11-02",employeeToken).size());
    }
    @ParameterizedTest @ValueSource(strings = {"/api/shifts/me", "/api/schedules/me"})
    void invalidDatesAndRangesReturn400(String path) throws Exception {
        for (String query : List.of("?startDate=bad", "?endDate=2026-02-30", "?startDate=2026-10-27&endDate=2026-10-26", "?startDate=0000-01-01"))
            status(400,send("GET",path+query,null,employeeToken));
    }
    @ParameterizedTest @ValueSource(strings = {"/api/shifts/me", "/api/schedules/me"})
    void employeeEndpointsRequireEmployeeRole(String path) throws Exception {
        status(401,send("GET",path,null,null)); status(403,send("GET",path,null,adminToken));
        status(401,send("GET",path,null,"invalid")); status(200,send("GET",path,null,employeeToken));
        String unknown = jwt.generate(UUID.randomUUID().toString(),Map.of("role","EMPLOYEE"));
        status(404,send("GET",path,null,unknown));
    }
    @Test void publishingRequiresAdminRole() throws Exception {
        status(401,send("POST",publishUrl(),null,null));
        status(403,send("POST",publishUrl(),null,employeeToken));
        assertEquals(ScheduleStatus.DRAFT,schedules.findById(schedule.getId()).orElseThrow().getStatus());
        publish();
    }
    @Test void simultaneousPublicationsHaveExactlyOneWinner() throws Exception {
        assertOneWinner(concurrent(4, () -> send("POST",publishUrl(),null,adminToken).statusCode()),200,409);
    }

    // Hold the winning transaction open until PostgreSQL reports that the competing
    // operation is waiting on a lock. This proves both orderings without timing sleeps.
    @ParameterizedTest @ValueSource(strings = {"create", "update", "delete"})
    void publisherWinningLockMakesConcurrentMutationConflict(String operation) throws Exception {
        String id = create(alice,MONDAY,"09:00","12:00").get("id").asString();
        var transactions = new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        var service = context.getBean(com.farouk.employee_scheduler.service.WeeklyScheduleService.class);
        var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        try (var executor = Executors.newSingleThreadExecutor()) {
            transactions.executeWithoutResult(tx -> {
                service.publish(schedule.getId());
                Future<Integer> competing = executor.submit(() -> switch (operation) {
                    case "create" -> send("POST",createUrl(),body(alice.getId(),MONDAY,"13:00","16:00"),adminToken).statusCode();
                    case "update" -> send("PUT","/api/admin/shifts/"+id,body(bob.getId(),MONDAY,"10:00","16:00"),adminToken).statusCode();
                    default -> send("DELETE","/api/admin/shifts/"+id,null,adminToken).statusCode();
                });
                waitForDatabaseLock(jdbc, competing);
                pending = competing;
            });
            assertEquals(409,pending.get(10,TimeUnit.SECONDS));
        }
        assertEquals(1,shifts.count()); assertEquals(alice.getId(),shifts.findById(UUID.fromString(id)).orElseThrow().getEmployee().getId());
    }
    private Future<Integer> pending;
    private void waitForDatabaseLock(org.springframework.jdbc.core.JdbcTemplate jdbc, Future<?> competing) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            assertFalse(competing.isDone(), "Competing operation must wait for the schedule transaction");
            jdbc.execute("select pg_stat_clear_snapshot()");
            if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and wait_event_type='Lock' and query ilike '%weekly_schedule%')",Boolean.class))) return;
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        fail("Competing request never waited on the schedule lock");
    }
    @Test void draftEditWinningLockCommitsBeforePublisherWithoutLostAssignment() throws Exception {
        String id = create(alice,MONDAY,"09:00","12:00").get("id").asString();
        var transactions = new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        var service = context.getBean(com.farouk.employee_scheduler.service.ShiftService.class);
        try (var executor = Executors.newSingleThreadExecutor()) {
            transactions.executeWithoutResult(tx -> {
                service.update(UUID.fromString(id),new com.farouk.employee_scheduler.dto.ShiftRequest(bob.getId(),MONDAY,LocalTime.of(10,0),LocalTime.of(16,0)));
                pending = executor.submit(() -> send("POST",publishUrl(),null,adminToken).statusCode());
                waitForDatabaseLock(context.getBean(org.springframework.jdbc.core.JdbcTemplate.class),pending);
            });
            assertEquals(200,pending.get(10,TimeUnit.SECONDS));
        }
        Shift result = shifts.findById(UUID.fromString(id)).orElseThrow();
        assertEquals(bob.getId(),result.getEmployee().getId()); assertEquals(LocalTime.of(10,0),result.getStartTime());
        assertEquals(ScheduleStatus.PUBLISHED,schedules.findById(schedule.getId()).orElseThrow().getStatus());
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
