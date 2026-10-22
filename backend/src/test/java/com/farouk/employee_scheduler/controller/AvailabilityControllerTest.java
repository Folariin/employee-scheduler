package com.farouk.employee_scheduler.controller;

import com.farouk.employee_scheduler.config.SecurityConfig;
import com.farouk.employee_scheduler.dto.AvailabilityResponse;
import com.farouk.employee_scheduler.model.Availability;
import com.farouk.employee_scheduler.model.Employee;
import com.farouk.employee_scheduler.security.JwtService;
import com.farouk.employee_scheduler.service.AvailabilityService;
import com.farouk.employee_scheduler.service.EmployeeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Assemble the real MVC/security stack directly to retain the project's JUnit 5 convention.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AvailabilityControllerTest {
    private static final String URL = "/api/availability/me";
    private static final String SECRET = "availability-tests-only-secret-0123456789abcdef";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID AVAILABILITY_ID = UUID.randomUUID();
    private static final String BODY = """
            {"dayOfWeek":"MONDAY","startTime":"09:00","endTime":"17:00"}
            """;
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private AvailabilityService service;
    private EmployeeService employeeService;
    private JwtService jwt;
    private Employee employee;

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({AvailabilityController.class, SecurityConfig.class})
    static class MvcConfig {
        @Bean AvailabilityService availabilityService() { return mock(AvailabilityService.class); }
        @Bean EmployeeService employeeService() { return mock(EmployeeService.class); }
        @Bean JwtService jwtService() { return new JwtService(SECRET, 3_600_000, "test"); }
    }

    @BeforeAll
    void startContext() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(MvcConfig.class);
        context.refresh();
        service = context.getBean(AvailabilityService.class);
        employeeService = context.getBean(EmployeeService.class);
        jwt = context.getBean(JwtService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @BeforeEach
    void resetServices() {
        reset(service, employeeService);
        employee = new Employee();
        employee.setId(EMPLOYEE_ID);
    }

    @AfterAll
    void closeContext() { if (context != null) context.close(); }

    private void employeeExists() {
        when(employeeService.findByUserId(USER_ID.toString())).thenReturn(employee);
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.generate(USER_ID.toString(), Map.of("role", "EMPLOYEE")));
    }

    private AvailabilityResponse response() {
        return new AvailabilityResponse(AVAILABILITY_ID, EMPLOYEE_ID, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
    }

    @Test
    void creationReturnsSavedEntityWith201() throws Exception {
        employeeExists();
        var saved = new Availability();
        when(service.save(any())).thenReturn(saved);
        when(service.toResponse(saved)).thenReturn(response());
        mvc.perform(authenticated(post(URL)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.id").value(AVAILABILITY_ID.toString()))
                .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID.toString()))
                .andExpect(jsonPath("$.dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.startTime").value("09:00:00"))
                .andExpect(jsonPath("$.endTime").value("17:00:00"));
        var captured = org.mockito.ArgumentCaptor.forClass(Availability.class);
        verify(service).save(captured.capture());
        Assertions.assertSame(employee, captured.getValue().getEmployee());
        Assertions.assertEquals(DayOfWeek.MONDAY, captured.getValue().getDayOfWeek());
        Assertions.assertEquals(LocalTime.of(9, 0), captured.getValue().getStartTime());
        Assertions.assertEquals(LocalTime.of(17, 0), captured.getValue().getEndTime());
    }

    @Test
    void listUsesAuthenticatedEmployeeId() throws Exception {
        employeeExists();
        var existing = new Availability();
        when(service.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of(existing));
        when(service.toResponse(existing)).thenReturn(response());
        mvc.perform(authenticated(get(URL))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeeId").value(EMPLOYEE_ID.toString()));
        verify(service).findByEmployeeId(EMPLOYEE_ID);
        verify(service, never()).findAll();
    }

    @Test
    void emptyListReturns200() throws Exception {
        employeeExists();
        when(service.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of());
        mvc.perform(authenticated(get(URL))).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void updateUsesAuthenticatedEmployeeAndUrlDay() throws Exception {
        employeeExists();
        var updated = new Availability();
        when(service.updateAvailability(EMPLOYEE_ID, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))
                .thenReturn(updated);
        when(service.toResponse(updated)).thenReturn(response());
        mvc.perform(authenticated(put(URL + "/MONDAY")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(AVAILABILITY_ID.toString()));
        verify(service).updateAvailability(EMPLOYEE_ID, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
    }

    @Test
    void conflictingUrlAndBodyDayReturns400WithoutChanges() throws Exception {
        mvc.perform(authenticated(put(URL + "/TUESDAY")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service, employeeService);
    }

    @Test
    void deleteUsesAuthenticatedEmployeeAndReturns204() throws Exception {
        employeeExists();
        mvc.perform(authenticated(delete(URL + "/MONDAY"))).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).deleteAvailability(EMPLOYEE_ID, DayOfWeek.MONDAY);
    }

    @Test
    void duplicateDayReturns409() throws Exception {
        employeeExists();
        when(service.save(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        mvc.perform(authenticated(endpoint("POST"))).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void missingAvailabilityReturns404(String method) throws Exception {
        employeeExists();
        if (method.equals("PUT")) {
            when(service.updateAvailability(eq(EMPLOYEE_ID), eq(DayOfWeek.MONDAY), any(), any()))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        } else {
            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(service).deleteAvailability(EMPLOYEE_ID, DayOfWeek.MONDAY);
        }
        mvc.perform(authenticated(endpoint(method))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "GET", "PUT", "DELETE"})
    void missingEmployeeProfileReturns404(String method) throws Exception {
        when(employeeService.findByUserId(USER_ID.toString())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(authenticated(endpoint(method))).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    static Stream<String> invalidBodies() {
        return Stream.concat(Stream.of("dayOfWeek", "startTime", "endTime")
                        .flatMap(field -> Stream.of("null", "\"\"")
                                .map(value -> BODY.replaceAll("\"" + field + "\":\"[^\"]*\"", "\"" + field + "\":" + value))),
                Stream.of("{}", "{", "", BODY.replace("MONDAY", "NOT_A_DAY"), BODY.replace("MONDAY", "monday"),
                        BODY.replace("09:00", "24:00"), BODY.replace("17:00", "17:99"), BODY.replace("09:00", "not-a-time")));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidPostBodyReturns400(String body) throws Exception {
        mvc.perform(authenticated(post(URL)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service, employeeService);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidPutBodyReturns400(String body) throws Exception {
        mvc.perform(authenticated(put(URL + "/MONDAY")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service, employeeService);
    }

    @ParameterizedTest
    @CsvSource({"PUT,not-a-day", "DELETE,not-a-day", "PUT,monday", "DELETE,8"})
    void invalidUrlDayReturns400(String method, String day) throws Exception {
        mvc.perform(authenticated(request(org.springframework.http.HttpMethod.valueOf(method), URL + "/" + day))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service, employeeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "GET", "PUT", "DELETE"})
    void anonymousRequestsReturn401(String method) throws Exception {
        mvc.perform(endpoint(method)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, employeeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "GET", "PUT", "DELETE"})
    void adminRequestsReturn403(String method) throws Exception {
        mvc.perform(endpoint(method).header("Authorization", "Bearer " + jwt.generate(USER_ID.toString(), Map.of("role", "ADMIN"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service, employeeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-token", "Bearer", "Basic abc", "Bearer "})
    void malformedAuthorizationReturns401(String header) throws Exception {
        mvc.perform(get(URL).header("Authorization", header)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, employeeService);
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        String token = new JwtService(SECRET, -60_000, "test").generate(USER_ID.toString(), Map.of("role", "EMPLOYEE"));
        mvc.perform(get(URL).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, employeeService);
    }

    @Test
    void tokenWithWrongSignatureReturns401() throws Exception {
        String token = new JwtService("different-signing-key-for-test-0123456789", 60_000, "test")
                .generate(USER_ID.toString(), Map.of("role", "EMPLOYEE"));
        mvc.perform(get(URL).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, employeeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void tokenWithoutUsableSubjectReturns401(String subject) throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer " + jwt.generate(subject, Map.of("role", "EMPLOYEE"))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service, employeeService);
    }

    @Test
    void tokenWithoutRoleReturns401() throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer " + jwt.generate(USER_ID.toString(), Map.of())))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service, employeeService);
    }

    private MockHttpServletRequestBuilder endpoint(String method) {
        return switch (method) {
            case "POST" -> post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY);
            case "GET" -> get(URL);
            case "PUT" -> put(URL + "/MONDAY").contentType(MediaType.APPLICATION_JSON).content(BODY);
            case "DELETE" -> delete(URL + "/MONDAY");
            default -> throw new IllegalArgumentException(method);
        };
    }
}
