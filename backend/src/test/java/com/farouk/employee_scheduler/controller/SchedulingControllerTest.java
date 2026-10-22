package com.farouk.employee_scheduler.controller;

import com.farouk.employee_scheduler.config.SecurityConfig;
import com.farouk.employee_scheduler.dto.*;
import com.farouk.employee_scheduler.model.ScheduleStatus;
import com.farouk.employee_scheduler.security.JwtService;
import com.farouk.employee_scheduler.service.ShiftService;
import com.farouk.employee_scheduler.service.WeeklyScheduleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchedulingControllerTest {
    private static final UUID SCHEDULE = UUID.randomUUID();
    private static final UUID SHIFT = UUID.randomUUID();
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
    private static final String SCHEDULE_BODY = "{\"weekStartDate\":\"2026-10-26\"}";
    private static final String SHIFT_BODY = "{\"employeeId\":\"" + EMPLOYEE + "\",\"shiftDate\":\"2026-10-26\",\"startTime\":\"10:00\",\"endTime\":\"16:00\"}";
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private WeeklyScheduleService schedules;
    private ShiftService shifts;
    private JwtService jwt;

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, WeeklyScheduleController.class, ShiftController.class})
    static class MvcConfig {
        @Bean WeeklyScheduleService weeklyScheduleService() { return mock(WeeklyScheduleService.class); }
        @Bean ShiftService shiftService() { return mock(ShiftService.class); }
        @Bean JwtService jwtService() { return new JwtService("scheduling-controller-tests-secret-0123456789", 3_600_000, "test"); }
    }

    @BeforeAll
    void start() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(MvcConfig.class); context.refresh();
        schedules = context.getBean(WeeklyScheduleService.class); shifts = context.getBean(ShiftService.class);
        jwt = context.getBean(JwtService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @BeforeEach
    void resetServices() { reset(schedules, shifts); }
    @AfterAll
    void close() { if (context != null) context.close(); }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.generate(UUID.randomUUID().toString(), Map.of("role", "ADMIN")));
    }

    private WeeklyScheduleResponse summary() { return new WeeklyScheduleResponse(SCHEDULE, MONDAY, ScheduleStatus.DRAFT, Instant.EPOCH); }
    private ShiftRequest assignment() { return new ShiftRequest(EMPLOYEE, MONDAY, LocalTime.of(10, 0), LocalTime.of(16, 0)); }
    private ShiftResponse shift() { return new ShiftResponse(SHIFT, SCHEDULE, EMPLOYEE, MONDAY, LocalTime.of(10, 0), LocalTime.of(16, 0)); }

    @Test
    void createsDraftScheduleWith201AndPublicFields() throws Exception {
        when(schedules.create(new CreateWeeklyScheduleRequest(MONDAY))).thenReturn(summary());
        mvc.perform(admin(endpoint("CREATE_SCHEDULE"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(4))).andExpect(jsonPath("$.id").value(SCHEDULE.toString()))
                .andExpect(jsonPath("$.weekStartDate").value("2026-10-26"))
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.createdAt").value("1970-01-01T00:00:00Z"));
        verify(schedules).create(new CreateWeeklyScheduleRequest(MONDAY));
    }

    @Test
    void listsSchedulesWith200() throws Exception {
        when(schedules.findAll()).thenReturn(List.of(summary()));
        mvc.perform(admin(endpoint("LIST"))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(SCHEDULE.toString()));
    }

    @Test
    void getsScheduleIncludingAssignedShifts() throws Exception {
        when(schedules.getDetail(SCHEDULE)).thenReturn(new WeeklyScheduleDetailResponse(SCHEDULE, MONDAY, ScheduleStatus.DRAFT, Instant.EPOCH, List.of(shift())));
        mvc.perform(admin(endpoint("DETAIL"))).andExpect(status().isOk()).andExpect(jsonPath("$.shifts", hasSize(1)))
                .andExpect(jsonPath("$.shifts[0].employeeId").value(EMPLOYEE.toString()));
    }

    @Test
    void createsShiftWith201() throws Exception {
        when(shifts.create(SCHEDULE, assignment())).thenReturn(shift());
        mvc.perform(admin(endpoint("CREATE_SHIFT"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(6))).andExpect(jsonPath("$.id").value(SHIFT.toString()))
                .andExpect(jsonPath("$.scheduleId").value(SCHEDULE.toString()))
                .andExpect(jsonPath("$.employeeId").value(EMPLOYEE.toString()))
                .andExpect(jsonPath("$.shiftDate").value("2026-10-26"))
                .andExpect(jsonPath("$.startTime").value("10:00:00")).andExpect(jsonPath("$.endTime").value("16:00:00"));
        verify(shifts).create(SCHEDULE, assignment());
    }

    @Test
    void updatesShiftWith200() throws Exception {
        when(shifts.update(SHIFT, assignment())).thenReturn(shift());
        mvc.perform(admin(endpoint("UPDATE_SHIFT"))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(SHIFT.toString()));
        verify(shifts).update(SHIFT, assignment());
    }

    @Test
    void deletesShiftWith204AndEmptyBody() throws Exception {
        mvc.perform(admin(endpoint("DELETE_SHIFT"))).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(shifts).delete(SHIFT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE_SCHEDULE", "LIST", "DETAIL", "CREATE_SHIFT", "UPDATE_SHIFT", "DELETE_SHIFT"})
    void anonymousRequestsAre401(String route) throws Exception {
        mvc.perform(endpoint(route)).andExpect(status().isUnauthorized()); verifyNoInteractions(schedules, shifts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE_SCHEDULE", "LIST", "DETAIL", "CREATE_SHIFT", "UPDATE_SHIFT", "DELETE_SHIFT"})
    void employeeRequestsAre403(String route) throws Exception {
        mvc.perform(endpoint(route).header("Authorization", "Bearer " + jwt.generate(UUID.randomUUID().toString(), Map.of("role", "EMPLOYEE"))))
                .andExpect(status().isForbidden()); verifyNoInteractions(schedules, shifts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE_SCHEDULE", "LIST", "DETAIL", "CREATE_SHIFT", "UPDATE_SHIFT", "DELETE_SHIFT"})
    void invalidBearerIs401(String route) throws Exception {
        mvc.perform(endpoint(route).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        verifyNoInteractions(schedules, shifts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"weekStartDate\":null}", "{\"weekStartDate\":\"not-a-date\"}", "{\"weekStartDate\":\"2026-02-30\"}", "", "{"})
    void malformedScheduleBodyIs400(String body) throws Exception {
        mvc.perform(admin(post("/api/admin/schedules")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(schedules, shifts);
    }

    static Stream<String> invalidShifts() {
        return Stream.concat(Stream.of("employeeId", "shiftDate", "startTime", "endTime")
                .map(field -> SHIFT_BODY.replaceAll("\"" + field + "\":\"[^\"]*\"", "\"" + field + "\":null")),
                Stream.of("{}", "{", "", SHIFT_BODY.replace(EMPLOYEE.toString(), "not-a-uuid"),
                        SHIFT_BODY.replace("2026-10-26", "2026-13-01"), SHIFT_BODY.replace("10:00", "24:00"),
                        SHIFT_BODY.replace("16:00", "16:99")));
    }

    @ParameterizedTest
    @MethodSource("invalidShifts")
    void invalidCreateBodyIs400(String body) throws Exception {
        mvc.perform(admin(post("/api/admin/schedules/" + SCHEDULE + "/shifts")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()); verifyNoInteractions(shifts, schedules);
    }

    @ParameterizedTest
    @MethodSource("invalidShifts")
    void invalidUpdateBodyIs400(String body) throws Exception {
        mvc.perform(admin(put("/api/admin/shifts/" + SHIFT)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()); verifyNoInteractions(shifts, schedules);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DETAIL", "CREATE_SHIFT", "UPDATE_SHIFT", "DELETE_SHIFT"})
    void malformedUrlIdsAre400(String route) throws Exception {
        MockHttpServletRequestBuilder request = switch (route) {
            case "DETAIL" -> get("/api/admin/schedules/not-a-uuid");
            case "CREATE_SHIFT" -> post("/api/admin/schedules/not-a-uuid/shifts").contentType(MediaType.APPLICATION_JSON).content(SHIFT_BODY);
            case "UPDATE_SHIFT" -> put("/api/admin/shifts/not-a-uuid").contentType(MediaType.APPLICATION_JSON).content(SHIFT_BODY);
            default -> delete("/api/admin/shifts/not-a-uuid");
        };
        mvc.perform(admin(request)).andExpect(status().isBadRequest()); verifyNoInteractions(shifts, schedules);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DETAIL", "CREATE_SHIFT", "UPDATE_SHIFT", "DELETE_SHIFT"})
    void missingResourcesAre404(String route) throws Exception {
        var missing = new ResponseStatusException(HttpStatus.NOT_FOUND);
        switch (route) {
            case "DETAIL" -> when(schedules.getDetail(SCHEDULE)).thenThrow(missing);
            case "CREATE_SHIFT" -> when(shifts.create(eq(SCHEDULE), any())).thenThrow(missing);
            case "UPDATE_SHIFT" -> when(shifts.update(eq(SHIFT), any())).thenThrow(missing);
            default -> doThrow(missing).when(shifts).delete(SHIFT);
        }
        mvc.perform(admin(endpoint(route))).andExpect(status().isNotFound());
    }

    @Test
    void duplicateScheduleIs409AndInvalidMondayIs400() throws Exception {
        when(schedules.create(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        mvc.perform(admin(endpoint("CREATE_SCHEDULE"))).andExpect(status().isConflict());
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST)).when(schedules).create(any());
        mvc.perform(admin(endpoint("CREATE_SCHEDULE"))).andExpect(status().isBadRequest());
    }

    @Test
    void shiftConflictIs409() throws Exception {
        when(shifts.create(eq(SCHEDULE), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        when(shifts.update(eq(SHIFT), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        mvc.perform(admin(endpoint("CREATE_SHIFT"))).andExpect(status().isConflict());
        mvc.perform(admin(endpoint("UPDATE_SHIFT"))).andExpect(status().isConflict());
    }

    private MockHttpServletRequestBuilder endpoint(String route) {
        return switch (route) {
            case "CREATE_SCHEDULE" -> post("/api/admin/schedules").contentType(MediaType.APPLICATION_JSON).content(SCHEDULE_BODY);
            case "LIST" -> get("/api/admin/schedules");
            case "DETAIL" -> get("/api/admin/schedules/" + SCHEDULE);
            case "CREATE_SHIFT" -> post("/api/admin/schedules/" + SCHEDULE + "/shifts").contentType(MediaType.APPLICATION_JSON).content(SHIFT_BODY);
            case "UPDATE_SHIFT" -> put("/api/admin/shifts/" + SHIFT).contentType(MediaType.APPLICATION_JSON).content(SHIFT_BODY);
            case "DELETE_SHIFT" -> delete("/api/admin/shifts/" + SHIFT);
            default -> throw new IllegalArgumentException(route);
        };
    }
}
