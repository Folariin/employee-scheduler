package com.farouk.employee_scheduler.controller;

import com.farouk.employee_scheduler.config.SecurityConfig;
import com.farouk.employee_scheduler.dto.*;
import com.farouk.employee_scheduler.security.JwtService;
import com.farouk.employee_scheduler.service.AutomaticSchedulingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutomaticSchedulingControllerTest {
    private final UUID id=UUID.randomUUID(),token=UUID.randomUUID();
    private AnnotationConfigWebApplicationContext context;private MockMvc mvc;
    private AutomaticSchedulingService service;private JwtService jwt;
    private final String body="{\"requirements\":[{\"date\":\"2026-10-26\",\"startTime\":\"09:00\",\"endTime\":\"17:00\",\"requiredEmployees\":1}],\"maxWeeklyHours\":40,\"balanceHoursFairly\":true}";
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,AutomaticSchedulingController.class})
    static class Config {
        @Bean AutomaticSchedulingService service(){return mock(AutomaticSchedulingService.class);}
        @Bean JwtService jwt(){return new JwtService("automatic-scheduling-controller-tests-secret-0123456789",3600000,"test");}
    }
    @BeforeAll void start(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();service=context.getBean(AutomaticSchedulingService.class);jwt=context.getBean(JwtService.class);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @BeforeEach void resetService(){reset(service);}
    @AfterAll void close(){context.close();}
    private String bearer(String role){return "Bearer "+jwt.generate("admin-user",Map.of("role",role));}
    private String path(String action){return "/api/admin/schedules/"+id+"/"+action;}
    private String applyBody(){return "{\"previewToken\":\""+token+"\",\"suggestionIds\":[\"0:employee\"]}";}
    @Test void adminPreviewsWithAuthenticatedOwnerAndNoCreationStatus() throws Exception {
        when(service.preview(eq(id),any(),eq("admin-user"))).thenReturn(new GenerationPreview(token,id,Instant.now().plusSeconds(600),List.of(),List.of(),List.of()));
        mvc.perform(post(path("generate-preview")).header("Authorization",bearer("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.previewToken").value(token.toString()));
        verify(service).preview(eq(id),any(),eq("admin-user"));
    }
    @Test void adminAppliesOnlyTokenAndSelection() throws Exception {
        when(service.apply(eq(id),any(),eq("admin-user"))).thenReturn(List.of());
        mvc.perform(post(path("apply-generated")).header("Authorization",bearer("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(applyBody())).andExpect(status().isOk());
        verify(service).apply(eq(id),eq(new ApplyGenerationRequest(token,List.of("0:employee"))),eq("admin-user"));
    }
    @ParameterizedTest @ValueSource(strings={"generate-preview","apply-generated"}) void anonymousIs401EmployeeIs403(String action) throws Exception {
        String input=action.equals("generate-preview")?body:applyBody();
        mvc.perform(post(path(action)).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isUnauthorized());
        mvc.perform(post(path(action)).header("Authorization",bearer("EMPLOYEE")).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={
        "{}", "{\"requirements\":[],\"maxWeeklyHours\":40}",
        "{\"requirements\":[null],\"maxWeeklyHours\":40}",
        "{\"requirements\":[{\"date\":\"2026-10-26\",\"startTime\":\"09:00\",\"endTime\":\"17:00\",\"requiredEmployees\":0}],\"maxWeeklyHours\":40}",
        "{\"requirements\":[{\"date\":\"2026-10-26\",\"startTime\":\"09:00\",\"endTime\":\"17:00\",\"requiredEmployees\":1}],\"maxWeeklyHours\":0}"
    }) void invalidPreviewInputIs400BeforeService(String input) throws Exception {
        mvc.perform(post(path("generate-preview")).header("Authorization",bearer("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"previewToken\":\"bad\",\"suggestionIds\":[\"x\"]}","{\"previewToken\":\"00000000-0000-0000-0000-000000000001\",\"suggestionIds\":[]}"})
    void invalidApplyInputIs400(String input) throws Exception {
        mvc.perform(post(path("apply-generated")).header("Authorization",bearer("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void concurrentDatabaseChangesBecome409() throws Exception {
        when(service.apply(any(),any(),any())).thenThrow(new org.springframework.dao.CannotAcquireLockException("race"));
        mvc.perform(post(path("apply-generated")).header("Authorization",bearer("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(applyBody())).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("new preview")));
    }
}
