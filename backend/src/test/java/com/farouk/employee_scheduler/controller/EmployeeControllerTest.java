package com.farouk.employee_scheduler.controller;

import com.farouk.employee_scheduler.config.SecurityConfig;
import com.farouk.employee_scheduler.dto.CreateEmployeeRequest;
import com.farouk.employee_scheduler.dto.EmployeeResponse;
import com.farouk.employee_scheduler.dto.UpdateEmployeeRequest;
import com.farouk.employee_scheduler.model.Employee;
import com.farouk.employee_scheduler.security.JwtService;
import com.farouk.employee_scheduler.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

// Spring 7.1's SpringExtension requires JUnit 6. Build a focused MVC context
// directly so the suite can use JUnit 5 and still exercise the real filter chain.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmployeeControllerTest {
    private static final String URL = "/api/admin/employees";
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CREATE = """
            {"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com",
             "employeeNumber":"EMP001","phoneNumber":"2045550100",
             "hireDate":"2026-01-15","temporaryPassword":"temporary-password"}
            """;
    private static final String UPDATE = """
            {"firstName":"Grace","lastName":"Hopper","phoneNumber":"2045550199","hireDate":"2026-02-01"}
            """;

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private EmployeeService service;
    private JwtService jwtService;

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({EmployeeController.class, SecurityConfig.class})
    static class MvcConfig {
        @Bean EmployeeService employeeService() { return mock(EmployeeService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
    }

    @BeforeAll
    void startMvcContext() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(MvcConfig.class);
        context.refresh();
        service = context.getBean(EmployeeService.class);
        jwtService = context.getBean(JwtService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @BeforeEach
    void resetMocks() {
        reset(service, jwtService);
    }

    @AfterAll
    void closeMvcContext() {
        if (context != null) context.close();
    }

    private EmployeeResponse response() {
        return new EmployeeResponse(ID, USER_ID, "Ada", "Lovelace", "ada@example.com", "EMP001",
                "2045550100", LocalDate.of(2026, 1, 15));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return authenticated(request, "ADMIN");
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, String role) {
        // Exercise JwtAuthFilter and URL authorization; cryptographic token verification belongs to JwtService.
        when(jwtService.isValid("test-token")).thenReturn(true);
        when(jwtService.extractUserId("test-token")).thenReturn(USER_ID.toString());
        when(jwtService.extractRole("test-token")).thenReturn(role);
        return request.header("Authorization", "Bearer test-token");
    }

    @Test
    void createsEmployeeAndReturns201WithPublicFieldsOnly() throws Exception {
        var employee = new Employee();
        var request = new CreateEmployeeRequest("Ada", "Lovelace", "ada@example.com", "EMP001",
                "2045550100", LocalDate.of(2026, 1, 15), "temporary-password");
        when(service.createEmployee(request)).thenReturn(employee);
        when(service.toResponse(employee)).thenReturn(response());
        mvc.perform(admin(post(URL)).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(8)))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.employeeNumber").value("EMP001"))
                .andExpect(jsonPath("$.phoneNumber").value("2045550100"))
                .andExpect(jsonPath("$.hireDate").value("2026-01-15"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());
        verify(service).createEmployee(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Employee number already exists", "Email is already registered"})
    void duplicateEmployeeDataReturns409(String reason) throws Exception {
        when(service.createEmployee(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, reason));
        mvc.perform(admin(post(URL)).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isConflict());
        verify(service, never()).toResponse(any());
    }

    @Test
    void optionalPhoneNumberMayBeOmittedOnCreate() throws Exception {
        var employee = new Employee();
        when(service.createEmployee(any())).thenReturn(employee);
        when(service.toResponse(employee)).thenReturn(response());
        mvc.perform(admin(post(URL)).contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE.replace("\"phoneNumber\":\"2045550100\",", "")))
                .andExpect(status().isCreated());
        verify(service).createEmployee(new CreateEmployeeRequest("Ada", "Lovelace", "ada@example.com",
                "EMP001", null, LocalDate.of(2026, 1, 15), "temporary-password"));
    }

    @Test
    void optionalPhoneNumberMayBeOmittedOnUpdate() throws Exception {
        var employee = new Employee();
        when(service.updateEmployee(eq(ID), any())).thenReturn(employee);
        when(service.toResponse(employee)).thenReturn(response());
        mvc.perform(admin(put(URL + "/" + ID)).contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE.replace("\"phoneNumber\":\"2045550199\",", "")))
                .andExpect(status().isOk());
        verify(service).updateEmployee(ID,
                new UpdateEmployeeRequest("Grace", "Hopper", null, LocalDate.of(2026, 2, 1)));
    }

    @Test
    void retrievesAllEmployeesWith200() throws Exception {
        var employee = new Employee();
        when(service.findAll()).thenReturn(List.of(employee));
        when(service.toResponse(employee)).thenReturn(response());
        mvc.perform(admin(get(URL))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ID.toString()));
    }

    @Test
    void emptyEmployeeListReturns200AndEmptyArray() throws Exception {
        when(service.findAll()).thenReturn(List.of());
        mvc.perform(admin(get(URL))).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void retrievesEmployeeByIdWith200() throws Exception {
        var employee = new Employee();
        when(service.getEmployeeById(ID)).thenReturn(employee);
        when(service.toResponse(employee)).thenReturn(response());
        mvc.perform(admin(get(URL + "/" + ID))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));
        verify(service).getEmployeeById(ID);
    }

    @Test
    void updatesEmployeeWith200AndUpdatedResponse() throws Exception {
        var employee = new Employee();
        var update = new UpdateEmployeeRequest("Grace", "Hopper", "2045550199", LocalDate.of(2026, 2, 1));
        when(service.updateEmployee(ID, update)).thenReturn(employee);
        when(service.toResponse(employee)).thenReturn(new EmployeeResponse(ID, USER_ID, "Grace", "Hopper",
                "ada@example.com", "EMP001", "2045550199", update.hireDate()));
        mvc.perform(admin(put(URL + "/" + ID)).contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Grace"))
                .andExpect(jsonPath("$.lastName").value("Hopper"))
                .andExpect(jsonPath("$.phoneNumber").value("2045550199"))
                .andExpect(jsonPath("$.hireDate").value("2026-02-01"));
        verify(service).updateEmployee(ID, update);
    }

    @Test
    void deletesEmployeeWith204AndNoBody() throws Exception {
        mvc.perform(admin(delete(URL + "/" + ID))).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).deleteEmployee(ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void missingEmployeeReturns404(String method) throws Exception {
        var missing = new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found");
        switch (method) {
            case "GET" -> when(service.getEmployeeById(ID)).thenThrow(missing);
            case "PUT" -> when(service.updateEmployee(eq(ID), any())).thenThrow(missing);
            case "DELETE" -> doThrow(missing).when(service).deleteEmployee(ID);
        }
        mvc.perform(admin(endpoint(method))).andExpect(status().isNotFound());
    }

    static Stream<String> invalidCreates() {
        return Stream.concat(Stream.of("firstName", "lastName", "email", "employeeNumber", "temporaryPassword")
                .flatMap(field -> Stream.of("null", "\"\"", "\"   \"")
                        .map(value -> CREATE.replaceAll("\"" + field + "\":\"[^\"]*\"", "\"" + field + "\":" + value))),
                Stream.of(CREATE.replace("ada@example.com", "invalid-email"),
                        CREATE.replace("\"2026-01-15\"", "null"), "{}"));
    }

    @ParameterizedTest
    @MethodSource("invalidCreates")
    void invalidCreationReturns400BeforeCallingService(String body) throws Exception {
        mvc.perform(admin(post(URL)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    static Stream<String> invalidUpdates() {
        return Stream.concat(Stream.of("firstName", "lastName")
                .flatMap(field -> Stream.of("null", "\"\"", "\"   \"")
                        .map(value -> UPDATE.replaceAll("\"" + field + "\":\"[^\"]*\"", "\"" + field + "\":" + value))),
                Stream.of(UPDATE.replace("\"2026-02-01\"", "null"), "{}"));
    }

    @ParameterizedTest
    @MethodSource("invalidUpdates")
    void invalidUpdateReturns400BeforeCallingService(String body) throws Exception {
        mvc.perform(admin(put(URL + "/" + ID)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void malformedIdReturns400(String method) throws Exception {
        mvc.perform(admin(request(org.springframework.http.HttpMethod.valueOf(method), URL + "/not-a-uuid"))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "{\"hireDate\":\"not-a-date\"}"})
    void unreadableBodyReturns400(String body) throws Exception {
        mvc.perform(admin(post(URL)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIST", "POST", "GET", "PUT", "DELETE"})
    void anonymousRequestsReturn401ForEveryEndpoint(String method) throws Exception {
        mvc.perform(endpoint(method)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIST", "POST", "GET", "PUT", "DELETE"})
    void invalidBearerTokenReturns401ForEveryEndpoint(String method) throws Exception {
        mvc.perform(endpoint(method).header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"LIST,EMPLOYEE", "POST,EMPLOYEE", "GET,EMPLOYEE", "PUT,EMPLOYEE", "DELETE,EMPLOYEE",
            "LIST,USER", "POST,USER", "GET,USER", "PUT,USER", "DELETE,USER"})
    void nonAdminRequestsReturn403ForEveryEndpoint(String method, String role) throws Exception {
        mvc.perform(authenticated(endpoint(method), role)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private MockHttpServletRequestBuilder endpoint(String method) {
        return switch (method) {
            case "LIST" -> get(URL);
            case "POST" -> post(URL).contentType(MediaType.APPLICATION_JSON).content(CREATE);
            case "GET" -> get(URL + "/" + ID);
            case "PUT" -> put(URL + "/" + ID).contentType(MediaType.APPLICATION_JSON).content(UPDATE);
            case "DELETE" -> delete(URL + "/" + ID);
            default -> throw new IllegalArgumentException(method);
        };
    }
}
