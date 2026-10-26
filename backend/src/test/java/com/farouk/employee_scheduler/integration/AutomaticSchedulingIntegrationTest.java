package com.farouk.employee_scheduler.integration;

import com.farouk.employee_scheduler.EmployeeSchedulerApplication;
import com.farouk.employee_scheduler.model.*;
import com.farouk.employee_scheduler.repository.*;
import com.farouk.employee_scheduler.security.JwtService;
import com.farouk.employee_scheduler.support.SchedulingTestDatabaseConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="availability.test.jdbc-url",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutomaticSchedulingIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class LlmConfig {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        com.farouk.employee_scheduler.service.llm.StaffingInterpreter testInterpreter() {
            return org.mockito.Mockito.mock(com.farouk.employee_scheduler.service.llm.StaffingInterpreter.class);
        }
    }

    private final LocalDate monday=LocalDate.of(2026,10,26);
    private ConfigurableApplicationContext context;private HttpClient http;private String base,adminToken,employeeToken;
    private final ObjectMapper json=new ObjectMapper();
    private EmployeeRepository employees;private AvailabilityRepository availability;private ShiftRepository shifts;
    private WeeklyScheduleRepository schedules;private UserRepository users;private WeeklySchedule schedule;
    private Employee alice,bob;private JdbcTemplate jdbc;
    @BeforeAll void start() {
        String url=System.getProperty("availability.test.jdbc-url");assertTrue(url.matches("jdbc:postgresql://[^/]+/availability_test(?:\\?.*)?"));
        context=new SpringApplicationBuilder(EmployeeSchedulerApplication.class,SchedulingTestDatabaseConfig.class,LlmConfig.class).run(
            "--server.port=0","--server.address=127.0.0.1","--spring.datasource.url="+url,
            "--spring.datasource.username="+System.getProperty("availability.test.jdbc-user","postgres"),
            "--spring.datasource.password="+System.getProperty("availability.test.jdbc-password",""),
            "--spring.datasource.hikari.maximum-pool-size=8","--spring.jpa.hibernate.ddl-auto=create-drop",
            "--spring.jpa.show-sql=false","--spring.jpa.open-in-view=false","--app.bootstrap.admin.enabled=false",
            "--app.environment=test","--jwt.secret=automatic-integration-tests-secret-0123456789");
        base="http://127.0.0.1:"+((WebServerApplicationContext)context).getWebServer().getPort();http=HttpClient.newHttpClient();
        employees=context.getBean(EmployeeRepository.class);availability=context.getBean(AvailabilityRepository.class);
        shifts=context.getBean(ShiftRepository.class);schedules=context.getBean(WeeklyScheduleRepository.class);users=context.getBean(UserRepository.class);jdbc=context.getBean(JdbcTemplate.class);
    }
    @BeforeEach void fixtures() {
        removeTrigger();shifts.deleteAll();schedules.deleteAll();availability.deleteAll();employees.deleteAll();users.deleteAll();
        alice=employee("Alice","AUTO001");bob=employee("Bob","AUTO002");
        for(var employee:List.of(alice,bob))for(var day:DayOfWeek.values())
            availability.saveAndFlush(new Availability(day,LocalTime.of(9,0),LocalTime.of(17,0),employee));
        schedule=schedules.saveAndFlush(new WeeklySchedule(monday));
        var jwt=context.getBean(JwtService.class);
        adminToken=jwt.generate(UUID.randomUUID().toString(),Map.of("role","ADMIN"));
        employeeToken=jwt.generate(alice.getUser().getId().toString(),Map.of("role","EMPLOYEE"));
    }
    @AfterAll void stop(){if(context!=null){removeTrigger();context.close();}if(http!=null)http.close();}
    private Employee employee(String name,String number) {
        var user=users.saveAndFlush(new User(name+"@automatic.test.invalid","test-only-unused-hash",name,"Test",Role.EMPLOYEE));
        var employee=new Employee();employee.setEmployeeNumber(number);employee.setUser(user);return employees.saveAndFlush(employee);
    }
    private void removeTrigger(){jdbc.execute("drop trigger if exists test_generated_failure on shift");jdbc.execute("drop function if exists test_generated_failure()");}
    private String path(String action){return "/api/admin/schedules/"+schedule.getId()+"/"+action;}
    private String request(int count){return "{\"requirements\":[{\"date\":\"2026-10-26\",\"startTime\":\"09:00\",\"endTime\":\"17:00\",\"requiredEmployees\":"+count+"}],\"maxWeeklyHours\":40,\"balanceHoursFairly\":true}";}
    private HttpResponse<String> send(String path,String body,String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
        if(token!=null)builder.header("Authorization","Bearer "+token);
        return http.send(builder.POST(body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> send(String action,String body) throws Exception {return send(path(action),body,adminToken);}
    private void status(int expected,HttpResponse<String> response){assertEquals(expected,response.statusCode(),response.body());}
    private JsonNode preview(int count) throws Exception {var response=send("generate-preview",request(count));status(200,response);return json.readTree(response.body());}
    private String selection(JsonNode preview){
        var ids=new ArrayList<String>();for(var assignment:preview.get("assignments"))ids.add(assignment.get("suggestionId").asString());
        return json.writeValueAsString(Map.of("previewToken",preview.get("previewToken").asString(),"suggestionIds",ids));
    }
    @Test void interpretationUsesOnlySelectedWeekAndNeverWritesOrCreatesPreview() throws Exception {
        var provider=context.getBean(com.farouk.employee_scheduler.service.llm.StaffingInterpreter.class);
        org.mockito.Mockito.reset(provider);
        String output="""
          {"requirements":[{"day":"TUESDAY","startTime":"09:00","endTime":"17:00","requiredEmployees":2,"mode":"FIXED"}],
          "maxWeeklyHours":null,"maxWeeklyHoursEvidence":null,"balanceHoursFairly":null,"fairnessEvidence":null,
          "clarificationQuestions":[],"unsupportedConstraints":[],"summary":"Two full Tuesday shifts."}
          """;
        org.mockito.Mockito.when(provider.interpret(org.mockito.Mockito.eq(monday),org.mockito.Mockito.anyString())).thenReturn(output);
        Shift existing=shifts.saveAndFlush(new Shift(schedule,alice,monday,LocalTime.of(9,0),LocalTime.NOON));
        var before=jdbc.queryForList("select * from shift order by id");
        var result=send("interpret","{\"instructions\":\"Two full shifts Tuesday 9 AM to 5 PM\"}");status(200,result);
        assertEquals("2026-10-27",json.readTree(result.body()).get("requirements").get(0).get("date").asString());
        assertEquals(before,jdbc.queryForList("select * from shift order by id"));assertTrue(shifts.existsById(existing.getId()));
        assertEquals(2,employees.count());assertEquals(2,users.count());assertEquals(14,availability.count());assertEquals(1,schedules.count());
        assertEquals(ScheduleStatus.DRAFT,schedules.findById(schedule.getId()).orElseThrow().getStatus());
        status(401,send(path("interpret"),"{}",null));status(403,send(path("interpret"),"{}",employeeToken));
        status(404,send("/api/admin/schedules/"+UUID.randomUUID()+"/interpret","{\"instructions\":\"test\"}",adminToken));
        status(200,send("publish",null));status(409,send("interpret","{\"instructions\":\"test\"}"));
        org.mockito.Mockito.verify(provider).interpret(monday,"Two full shifts Tuesday 9 AM to 5 PM");
        org.mockito.Mockito.verifyNoMoreInteractions(provider);
    }
    @Test void previewIsReadOnlyAndDeterministicAndDoesNotExposeAccountData() throws Exception {
        var first=preview(2);var second=preview(2);assertEquals(first.get("assignments"),second.get("assignments"));
        assertEquals(0,shifts.count());assertEquals(1,schedules.count());assertEquals(2,employees.count());assertEquals(14,availability.count());
        assertEquals(ScheduleStatus.DRAFT,schedules.findById(schedule.getId()).orElseThrow().getStatus());
        assertEquals(2,first.get("assignments").size());
        for(String secret:List.of("password","hash","email","phone","@automatic"))assertFalse(first.toString().contains(secret));
    }
    @Test void approvedPreviewCreatesSeparateShiftsAndKeepsDraftAndExistingShifts() throws Exception {
        Shift existing=shifts.saveAndFlush(new Shift(schedule,alice,monday.plusDays(1),LocalTime.of(9,0),LocalTime.NOON));
        var preview=preview(2);var response=send("apply-generated",selection(preview));status(200,response);
        assertEquals(2,json.readTree(response.body()).size());assertEquals(3,shifts.count());assertTrue(shifts.existsById(existing.getId()));
        assertEquals(ScheduleStatus.DRAFT,schedules.findById(schedule.getId()).orElseThrow().getStatus());
        status(409,send("apply-generated",selection(preview)));assertEquals(3,shifts.count());
        assertEquals(1,jdbc.queryForObject("select count(*) from pg_constraint where conrelid='shift'::regclass and conname='ex_shift_employee_time'",Integer.class));
    }
    @Test void managerCanSelectOnlyOneOfSeveralSuggestions() throws Exception {
        var preview=preview(2);var body=json.writeValueAsString(Map.of("previewToken",preview.get("previewToken").asString(),"suggestionIds",List.of(preview.get("assignments").get(0).get("suggestionId").asString())));
        status(200,send("apply-generated",body));assertEquals(1,shifts.count());status(409,send("apply-generated",body));
    }
    @Test void availabilityChangesMakePreviewStaleWithoutAnyWrites() throws Exception {
        var preview=preview(2);var window=availability.findByEmployee_IdAndDayOfWeek(alice.getId(),DayOfWeek.MONDAY).orElseThrow();
        window.setStartTime(LocalTime.of(10,0));availability.saveAndFlush(window);
        status(409,send("apply-generated",selection(preview)));assertEquals(0,shifts.count());
    }
    @Test void manualAssignmentsMakePreviewStale() throws Exception {
        var preview=preview(2);shifts.saveAndFlush(new Shift(schedule,bob,monday,LocalTime.of(9,0),LocalTime.NOON));
        status(409,send("apply-generated",selection(preview)));assertEquals(1,shifts.count());
    }
    @Test void tamperedSelectionsAndAnotherAdminCannotApply() throws Exception {
        var preview=preview(2);
        var body=json.writeValueAsString(Map.of("previewToken",preview.get("previewToken").asString(),"suggestionIds",List.of("forged-employee")));
        status(400,send("apply-generated",body));
        String other=context.getBean(JwtService.class).generate(UUID.randomUUID().toString(),Map.of("role","ADMIN"));
        status(409,send(path("apply-generated"),selection(preview),other));assertEquals(0,shifts.count());
    }
    @Test void databaseFailureOnSecondAssignmentRollsBackFirstAndAllowsRetry() throws Exception {
        var preview=preview(2);String second=preview.get("assignments").get(1).get("employeeId").asString();
        jdbc.execute("create function test_generated_failure() returns trigger language plpgsql as $$ begin if NEW.employee_id='"+second+"'::uuid then raise exception 'test exclusion conflict' using errcode='23P01'; end if; return NEW; end $$");
        jdbc.execute("create trigger test_generated_failure before insert on shift for each row execute function test_generated_failure()");
        status(409,send("apply-generated",selection(preview)));assertEquals(0,shifts.count());
        removeTrigger();status(200,send("apply-generated",selection(preview)));assertEquals(2,shifts.count());
    }
    @ParameterizedTest @ValueSource(booleans={true,false}) void concurrentApplicationsSaveExactlyOneBatch(boolean sameToken) throws Exception {
        var first=preview(2);var second=sameToken?first:preview(2);String endpoint=path("apply-generated");String token=adminToken;
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return send(endpoint,selection(first),token).statusCode();});
            var b=pool.submit(()->{gate.await();return send(endpoint,selection(second),token).statusCode();});gate.countDown();
            var statuses=new ArrayList<>(List.of(a.get(40,TimeUnit.SECONDS),b.get(40,TimeUnit.SECONDS)));Collections.sort(statuses);
            assertEquals(List.of(200,409),statuses);assertEquals(2,shifts.count());
        }
    }
    @Test void publicationRaceAlwaysLeavesAnImmutablePublishedSchedule() throws Exception {
        var preview=preview(2);String applyPath=path("apply-generated"),publishPath=path("publish"),token=adminToken;
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return send(applyPath,selection(preview),token).statusCode();});
            var p=pool.submit(()->{gate.await();return send(publishPath,null,token).statusCode();});gate.countDown();
            int applied=a.get(40,TimeUnit.SECONDS);assertEquals(200,p.get(40,TimeUnit.SECONDS));assertTrue(applied==200||applied==409);
            assertEquals(applied==200?2:0,shifts.count());assertEquals(ScheduleStatus.PUBLISHED,schedules.findById(schedule.getId()).orElseThrow().getStatus());
        }
        status(409,send("generate-preview",request(1)));status(409,send("apply-generated",selection(preview)));
    }
    @ParameterizedTest @ValueSource(strings={"generate-preview","apply-generated"}) void realJwtRoleAndAnonymousAuthorization(String action) throws Exception {
        String body=action.equals("generate-preview")?request(1):"{}";
        status(401,send(path(action),body,null));status(403,send(path(action),body,employeeToken));assertEquals(0,shifts.count());
    }
    @Test void insufficientAndEmptyTeamReturnShortages() throws Exception {
        assertEquals(1,preview(3).get("coverage").get(0).get("shortage").asInt());
        availability.deleteAll();employees.deleteAll();var none=preview(2);
        assertEquals(0,none.get("assignments").size());assertEquals(2,none.get("coverage").get(0).get("shortage").asInt());
    }
    @Test void malformedWeekAndOverlappingRequirementsAre400AndMissingSchedule404() throws Exception {
        status(400,send("generate-preview",request(1).replace("2026-10-26","2026-11-02")));
        String original=request(1);String interval="{\"date\":\"2026-10-26\",\"startTime\":\"10:00\",\"endTime\":\"12:00\",\"requiredEmployees\":1}";
        status(400,send("generate-preview",original.replace("}],","},"+interval+"],")));
        status(404,send("/api/admin/schedules/"+UUID.randomUUID()+"/generate-preview",request(1),adminToken));
    }
    private String flexibleRequest(int count,int min,int max) {
        return request(count).replace("\"requiredEmployees\":"+count,"\"requiredEmployees\":"+count+",\"mode\":\"FLEXIBLE\"")
                .replace("\"maxWeeklyHours\":40","\"maxWeeklyHours\":40,\"minShiftMinutes\":"+min+",\"maxShiftMinutes\":"+max);
    }
    private JsonNode flexiblePreview(int count,int min,int max) throws Exception {
        var response=send("generate-preview",flexibleRequest(count,min,max));status(200,response);return json.readTree(response.body());
    }
    private void mondayAvailability(Employee employee,String start,String end) {
        var window=availability.findByEmployee_IdAndDayOfWeek(employee.getId(),DayOfWeek.MONDAY).orElseThrow();
        window.setStartTime(LocalTime.parse(start));window.setEndTime(LocalTime.parse(end));availability.saveAndFlush(window);
    }
    @Test void flexibleStaggeredPreviewIsReadOnlyAndAppliesActualIndividualTimes() throws Exception {
        mondayAvailability(alice,"09:00","14:00");mondayAvailability(bob,"10:00","16:00");
        var third=employee("Charlie","AUTO003");availability.saveAndFlush(new Availability(DayOfWeek.MONDAY,LocalTime.of(14,0),LocalTime.of(17,0),third));
        long rows=availability.count();var preview=flexiblePreview(3,120,480);
        assertEquals(3,preview.get("assignments").size());assertEquals(0,preview.get("coverage").get(0).get("shortage").asInt());
        assertEquals("FLEXIBLE",preview.get("coverage").get(0).get("mode").asString());assertEquals(0,shifts.count());assertEquals(rows,availability.count());
        status(200,send("apply-generated",selection(preview)));assertEquals(3,shifts.count());
        for(var assignment:preview.get("assignments")) {
            var stored=shifts.findByEmployeeId(UUID.fromString(assignment.get("employeeId").asString())).getFirst();
            assertEquals(LocalTime.parse(assignment.get("startTime").asString()),stored.getStartTime());
            assertEquals(LocalTime.parse(assignment.get("endTime").asString()),stored.getEndTime());
        }
        assertEquals(ScheduleStatus.DRAFT,schedules.findById(schedule.getId()).orElseThrow().getStatus());
    }
    @Test void flexibleLimitsGenerateShortagesAndConfiguredDurationsPersistWithoutRounding() throws Exception {
        mondayAvailability(alice,"09:00","10:59:59");mondayAvailability(bob,"12:00","16:00");
        var preview=flexiblePreview(2,120,180);assertEquals(1,preview.get("assignments").size());
        assertEquals(1,preview.get("coverage").get(0).get("shortage").asInt());
        assertEquals("12:00:00",preview.get("assignments").get(0).get("startTime").asString());
        assertEquals("15:00:00",preview.get("assignments").get(0).get("endTime").asString());
        status(200,send("apply-generated",selection(preview)));assertEquals(1,shifts.count());
    }
    @Test void flexibleAlreadyAssignedEmployeeCountsOnceWithoutReassignment() throws Exception {
        var old=shifts.saveAndFlush(new Shift(schedule,alice,monday,LocalTime.of(9,0),LocalTime.of(10,0)));
        var preview=flexiblePreview(2,120,480);assertEquals(1,preview.get("coverage").get(0).get("existingEmployees").asInt());
        assertEquals(1,preview.get("assignments").size());assertEquals(bob.getId().toString(),preview.get("assignments").get(0).get("employeeId").asString());
        status(200,send("apply-generated",selection(preview)));assertTrue(shifts.existsById(old.getId()));assertEquals(2,shifts.count());
    }
    @Test void flexibleBatchRollsBackOnSecondInsertAndRetryUsesTheSameDurationPolicy() throws Exception {
        var preview=flexiblePreview(2,30,60);String second=preview.get("assignments").get(1).get("employeeId").asString();
        jdbc.execute("create function test_generated_failure() returns trigger language plpgsql as $$ begin if NEW.employee_id='"+second+"'::uuid then raise exception 'test exclusion conflict' using errcode='23P01'; end if; return NEW; end $$");
        jdbc.execute("create trigger test_generated_failure before insert on shift for each row execute function test_generated_failure()");
        status(409,send("apply-generated",selection(preview)));assertEquals(0,shifts.count());
        removeTrigger();status(200,send("apply-generated",selection(preview)));assertEquals(2,shifts.count());
        for(var shift:shifts.findAll())assertEquals(3600,Duration.between(shift.getStartTime(),shift.getEndTime()).getSeconds());
    }
    @Test void flexibleStaleAndPublishedPreviewsAreStillRefused() throws Exception {
        var preview=flexiblePreview(2,120,480);mondayAvailability(alice,"10:00","17:00");
        status(409,send("apply-generated",selection(preview)));assertEquals(0,shifts.count());
        var updated=flexiblePreview(2,120,480);status(200,send("publish",null));
        status(409,send("apply-generated",selection(updated)));status(409,send("generate-preview",flexibleRequest(2,120,480)));
    }
    @Test void concurrentFlexibleApplicationsCommitOnlyOneBatch() throws Exception {
        var first=flexiblePreview(2,120,480);var second=flexiblePreview(2,120,480);String endpoint=path("apply-generated"),token=adminToken;
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return send(endpoint,selection(first),token).statusCode();});
            var b=pool.submit(()->{gate.await();return send(endpoint,selection(second),token).statusCode();});gate.countDown();
            var results=new ArrayList<>(List.of(a.get(40,TimeUnit.SECONDS),b.get(40,TimeUnit.SECONDS)));Collections.sort(results);
            assertEquals(List.of(200,409),results);assertEquals(2,shifts.count());
        }
    }
    @ParameterizedTest @ValueSource(strings={"generate-preview","apply-generated"})
    void flexibleRequestsCannotBypassJwtRoles(String action) throws Exception {
        String body=action.equals("generate-preview")?flexibleRequest(2,120,480):"{}";
        status(401,send(path(action),body,null));status(403,send(path(action),body,employeeToken));assertEquals(0,shifts.count());
    }
    @ParameterizedTest @ValueSource(strings={"0,480","120,0","181,180","120,1441"})
    void invalidFlexibleLimitsAre400(String pair) throws Exception {
        var values=pair.split(",");status(400,send("generate-preview",flexibleRequest(1,Integer.parseInt(values[0]),Integer.parseInt(values[1]))));assertEquals(0,shifts.count());
    }
    @Test void unknownModeIs400AndOmittedModeRemainsFixed() throws Exception {
        status(400,send("generate-preview",flexibleRequest(1,120,480).replace("FLEXIBLE","UNKNOWN")));
        mondayAvailability(alice,"10:00","14:00");mondayAvailability(bob,"10:00","16:00");
        assertEquals(2,preview(2).get("coverage").get(0).get("shortage").asInt());
        assertEquals(0,flexiblePreview(2,120,480).get("coverage").get(0).get("shortage").asInt());
    }
}
