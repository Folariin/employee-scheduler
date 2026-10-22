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
class SchedulePublishingControllerTest {
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
    private com.farouk.employee_scheduler.service.EmployeeScheduleService employeeSchedules;

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, WeeklyScheduleController.class, EmployeeScheduleController.class})
    static class MvcConfig {
        @Bean WeeklyScheduleService weeklyScheduleService() { return mock(WeeklyScheduleService.class); }
        @Bean com.farouk.employee_scheduler.service.EmployeeScheduleService employeeSchedules() { return mock(com.farouk.employee_scheduler.service.EmployeeScheduleService.class); }
        @Bean ShiftService shiftService() { return mock(ShiftService.class); }
        @Bean JwtService jwtService() { return new JwtService("scheduling-controller-tests-secret-0123456789", 3_600_000, "test"); }
    }

    @BeforeAll
    void start() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(MvcConfig.class); context.refresh();
        schedules = context.getBean(WeeklyScheduleService.class); shifts = context.getBean(ShiftService.class);
        jwt = context.getBean(JwtService.class);
        employeeSchedules = context.getBean(com.farouk.employee_scheduler.service.EmployeeScheduleService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @BeforeEach
    void resetServices() { reset(schedules, shifts, employeeSchedules); }
    @AfterAll
    void close() { if (context != null) context.close(); }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.generate(UUID.randomUUID().toString(), Map.of("role", "ADMIN")));
    }

    private MockHttpServletRequestBuilder employee(MockHttpServletRequestBuilder request, String userId) {
        return request.header("Authorization", "Bearer " + jwt.generate(userId, Map.of("role", "EMPLOYEE")));
    }
    @Test void adminPublishesAndReceivesUpdatedStatus() throws Exception {
        when(schedules.publish(SCHEDULE)).thenReturn(new WeeklyScheduleResponse(SCHEDULE,MONDAY,ScheduleStatus.PUBLISHED,Instant.EPOCH));
        mvc.perform(admin(post("/api/admin/schedules/{id}/publish",SCHEDULE))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED")).andExpect(jsonPath("$.id").value(SCHEDULE.toString()));
        verify(schedules).publish(SCHEDULE);
    }
    @ParameterizedTest @ValueSource(ints = {404,409})
    void publishPreservesServiceErrors(int code) throws Exception {
        when(schedules.publish(SCHEDULE)).thenThrow(new ResponseStatusException(HttpStatus.valueOf(code)));
        mvc.perform(admin(post("/api/admin/schedules/{id}/publish",SCHEDULE))).andExpect(status().is(code));
    }
    @Test void publishRequiresAdminAndValidUuid() throws Exception {
        mvc.perform(post("/api/admin/schedules/{id}/publish",SCHEDULE)).andExpect(status().isUnauthorized());
        mvc.perform(employee(post("/api/admin/schedules/{id}/publish",SCHEDULE),EMPLOYEE.toString())).andExpect(status().isForbidden());
        mvc.perform(admin(post("/api/admin/schedules/bad/publish"))).andExpect(status().isBadRequest());
        verifyNoInteractions(schedules);
    }
    @ParameterizedTest @ValueSource(strings = {"/api/shifts/me","/api/schedules/me"})
    void employeeEndpointsRequireEmployeeRole(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(admin(get(path))).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        verifyNoInteractions(employeeSchedules);
    }
    @ParameterizedTest @ValueSource(strings = {"/api/shifts/me","/api/schedules/me"})
    void forwardsPrincipalAndIsoDateBoundsIgnoringClientEmployeeId(String path) throws Exception {
        String userId = UUID.randomUUID().toString();
        mvc.perform(employee(get(path).param("startDate","2026-10-26").param("endDate","2026-11-01")
                .param("employeeId",UUID.randomUUID().toString()),userId)).andExpect(status().isOk()).andExpect(content().json("[]"));
        if(path.contains("shifts")) verify(employeeSchedules).getMyShifts(userId,MONDAY,MONDAY.plusDays(6));
        else verify(employeeSchedules).getMySchedules(userId,MONDAY,MONDAY.plusDays(6));
    }
    @ParameterizedTest @ValueSource(strings = {"/api/shifts/me","/api/schedules/me"})
    void malformedDatesAre400BeforeService(String path) throws Exception {
        mvc.perform(employee(get(path).param("startDate","tomorrow"),EMPLOYEE.toString())).andExpect(status().isBadRequest());
        verifyNoInteractions(employeeSchedules);
    }
    @Test void shiftResponseUsesCalendarDtoShape() throws Exception {
        var response = new ShiftResponse(SHIFT,SCHEDULE,EMPLOYEE,MONDAY,LocalTime.of(9,0),LocalTime.NOON);
        when(employeeSchedules.getMyShifts(EMPLOYEE.toString(),null,null)).thenReturn(List.of(response));
        mvc.perform(employee(get("/api/shifts/me"),EMPLOYEE.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0]",aMapWithSize(6))).andExpect(jsonPath("$[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$[0].employeeId").value(EMPLOYEE.toString()));
    }
    @Test void scheduleResponseContainsOnlySuppliedOwnShiftDtos() throws Exception {
        var response = new ShiftResponse(SHIFT,SCHEDULE,EMPLOYEE,MONDAY,LocalTime.of(9,0),LocalTime.NOON);
        when(employeeSchedules.getMySchedules(EMPLOYEE.toString(),null,null)).thenReturn(List.of(
                new WeeklyScheduleDetailResponse(SCHEDULE,MONDAY,ScheduleStatus.PUBLISHED,Instant.EPOCH,List.of(response))));
        mvc.perform(employee(get("/api/schedules/me"),EMPLOYEE.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0]",aMapWithSize(5))).andExpect(jsonPath("$[0].shifts",hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }
}
