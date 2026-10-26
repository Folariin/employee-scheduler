package com.farouk.employee_scheduler.controller;
import com.farouk.employee_scheduler.config.SecurityConfig;
import com.farouk.employee_scheduler.dto.*;
import com.farouk.employee_scheduler.security.JwtService;
import com.farouk.employee_scheduler.service.StaffingInterpretationService;
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
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffingInterpretationControllerTest {
 private AnnotationConfigWebApplicationContext context;private MockMvc mvc;private StaffingInterpretationService service;private JwtService jwt;
 private final UUID id=UUID.randomUUID();private String path(){return "/api/admin/schedules/"+id+"/interpret";}
 @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity @Import({SecurityConfig.class,StaffingInterpretationController.class})
 static class Config {
  @Bean StaffingInterpretationService service(){return mock(StaffingInterpretationService.class);}
  @Bean JwtService jwt(){return new JwtService("llm-controller-test-secret-01234567890123456789",3600000,"test");}
 }
 @BeforeAll void start(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();service=context.getBean(StaffingInterpretationService.class);jwt=context.getBean(JwtService.class);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 @BeforeEach void resetService(){reset(service);}
 @AfterAll void close(){context.close();}
 private String token(String role){return "Bearer "+jwt.generate("test-admin",Map.of("role",role));}
 @Test void adminCanInterpret() throws Exception {when(service.interpret(eq(id),any())).thenReturn(new InterpretationResponse(List.of(),null,null,List.of("Which mode?"),List.of(),"Clarify"));mvc.perform(post(path()).header("Authorization",token("ADMIN")).contentType("application/json").content("{\"instructions\":\"Three Monday\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.clarificationQuestions[0]").value("Which mode?"));verify(service).interpret(id,new InterpretationRequest("Three Monday"));}
 @ParameterizedTest @ValueSource(strings={"EMPLOYEE","OTHER"}) void otherRolesForbidden(String role) throws Exception {mvc.perform(post(path()).header("Authorization",token(role)).contentType("application/json").content("{\"instructions\":\"test\"}")).andExpect(status().isForbidden());verifyNoInteractions(service);}
 @Test void anonymousUnauthorized() throws Exception {mvc.perform(post(path()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
 @ParameterizedTest @ValueSource(strings={"{}","{\"instructions\":null}","{\"instructions\":\"  \"}","{"}) void invalidInput(String body) throws Exception {mvc.perform(post(path()).header("Authorization",token("ADMIN")).contentType("application/json").content(body)).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 @Test void validationResponseAndLogsDoNotEchoSensitiveInstructions() throws Exception {
  var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver.class);
  var logs=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();logs.start();logger.addAppender(logs);
  try {
   mvc.perform(post(path()).header("Authorization",token("ADMIN")).contentType("application/json").content("{\"instructions\":\"private " + "a".repeat(4001) + "\"}"))
     .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
   org.junit.jupiter.api.Assertions.assertTrue(logs.list.stream().noneMatch(event->event.getFormattedMessage().contains("private")));
   verifyNoInteractions(service);
  } finally {logger.detachAppender(logs);logs.stop();}
 }
 @Test void oversizedInput() throws Exception {mvc.perform(post(path()).header("Authorization",token("ADMIN")).contentType("application/json").content("{\"instructions\":\""+"a".repeat(4001)+"\"}")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
}
