package com.farouk.employee_scheduler.controller;
import com.farouk.employee_scheduler.config.SecurityConfig;
import com.farouk.employee_scheduler.model.*;
import com.farouk.employee_scheduler.security.JwtService;
import com.farouk.employee_scheduler.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfileAvailabilityControllerTest {
 private AnnotationConfigWebApplicationContext context;private MockMvc mvc;private EmployeeService employees;private AvailabilityService availability;private JwtService jwt;
 private final UUID id=UUID.randomUUID();private String path(){return "/api/admin/employees/"+id+"/availability";}
 @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity @Import({SecurityConfig.class,EmployeeProfileController.class,AdminAvailabilityController.class})
 static class Config {
  @Bean EmployeeService employees(){return mock(EmployeeService.class);}
  @Bean AvailabilityService availability(){return mock(AvailabilityService.class);}
  @Bean JwtService jwt(){return new JwtService("profile-controller-test-secret-01234567890123456789",3600000,"test");}
 }
 @BeforeAll void start(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();employees=context.getBean(EmployeeService.class);availability=context.getBean(AvailabilityService.class);jwt=context.getBean(JwtService.class);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 @BeforeEach void resetServices(){reset(employees,availability);}
 @AfterAll void close(){context.close();}
 private String token(String role){return "Bearer "+jwt.generate(id.toString(),Map.of("role",role));}
 @Test void selfProfileUsesOnlyJwtIdentityAndReturnsOnlyNames() throws Exception {var user=new User("private@test.invalid","hash","Alice","Bennett",com.farouk.employee_scheduler.model.Role.EMPLOYEE);var employee=new Employee();employee.setUser(user);when(employees.findByUserId(id.toString())).thenReturn(employee);mvc.perform(get("/api/employees/me").header("Authorization",token("EMPLOYEE"))).andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Alice")).andExpect(jsonPath("$.lastName").value("Bennett")).andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());verify(employees).findByUserId(id.toString());verifyNoMoreInteractions(employees);}
 @Test void adminCanReadEmptyAvailability() throws Exception {when(availability.findByEmployeeId(id)).thenReturn(List.of());mvc.perform(get(path()).header("Authorization",token("ADMIN"))).andExpect(status().isOk()).andExpect(content().json("[]"));verify(employees).getEmployeeById(id);verify(availability).findByEmployeeId(id);}
 @Test void availabilityIsOrderedMondayToSunday() throws Exception {var employee=new Employee();employee.setId(id);var monday=new Availability(java.time.DayOfWeek.MONDAY,java.time.LocalTime.of(9,0),java.time.LocalTime.of(17,0),employee);var friday=new Availability(java.time.DayOfWeek.FRIDAY,java.time.LocalTime.of(10,0),java.time.LocalTime.of(16,0),employee);when(availability.findByEmployeeId(id)).thenReturn(List.of(friday,monday));when(availability.toResponse(any())).thenAnswer(call->{Availability row=call.getArgument(0);return new com.farouk.employee_scheduler.dto.AvailabilityResponse(null,id,row.getDayOfWeek(),row.getStartTime(),row.getEndTime());});mvc.perform(get(path()).header("Authorization",token("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY")).andExpect(jsonPath("$[1].dayOfWeek").value("FRIDAY"));}
 @Test void missingSelfProfileIs404() throws Exception {when(employees.findByUserId(id.toString())).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));mvc.perform(get("/api/employees/me").header("Authorization",token("EMPLOYEE"))).andExpect(status().isNotFound());}
 @Test void missingEmployeeIs404() throws Exception {when(employees.getEmployeeById(id)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));mvc.perform(get(path()).header("Authorization",token("ADMIN"))).andExpect(status().isNotFound());verifyNoInteractions(availability);}
 @Test void anonymousIs401() throws Exception {for(String url:List.of(path(),"/api/employees/me"))mvc.perform(get(url)).andExpect(status().isUnauthorized());verifyNoInteractions(employees,availability);}
 @Test void employeeCannotReadAnotherAvailability() throws Exception {mvc.perform(get(path()).header("Authorization",token("EMPLOYEE"))).andExpect(status().isForbidden());verifyNoInteractions(employees,availability);}
 @Test void adminCannotUseEmployeeProfile() throws Exception {mvc.perform(get("/api/employees/me").header("Authorization",token("ADMIN"))).andExpect(status().isForbidden());verifyNoInteractions(employees);}
 @Test void profileDoesNotAcceptAnotherEmployeePath() throws Exception {mvc.perform(get("/api/employees/"+UUID.randomUUID()).header("Authorization",token("EMPLOYEE"))).andExpect(status().isNotFound());verifyNoInteractions(employees);}
 @Test void adminAvailabilityIsReadOnly() throws Exception {for(var method:List.of(post(path()),put(path()),delete(path())))mvc.perform(method.header("Authorization",token("ADMIN"))).andExpect(status().isMethodNotAllowed());verifyNoInteractions(employees,availability);}
 @Test void invalidJwtIsUnauthorized() throws Exception {mvc.perform(get("/api/employees/me").header("Authorization","Bearer invalid.jwt.signature")).andExpect(status().isUnauthorized());verifyNoInteractions(employees);}
}

