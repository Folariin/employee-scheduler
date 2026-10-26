package com.farouk.employee_scheduler.service;
import com.farouk.employee_scheduler.dto.*;
import com.farouk.employee_scheduler.model.*;
import com.farouk.employee_scheduler.service.llm.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StaffingInterpretationServiceTest {
    private final LocalDate week=LocalDate.of(2026,10,26);
    private final UUID id=UUID.randomUUID();
    private WeeklyScheduleService schedules;private StaffingInterpreter provider;private StaffingInterpretationService service;
    private final JsonMapper json=JsonMapper.builder().build();
    private final String valid="""
        {"requirements":[{"day":"MONDAY","startTime":"09:00","endTime":"17:00","requiredEmployees":3,"mode":"FLEXIBLE"}],
        "maxWeeklyHours":null,"maxWeeklyHoursEvidence":null,"balanceHoursFairly":null,"fairnessEvidence":null,
        "clarificationQuestions":[],"unsupportedConstraints":[],"summary":"Three distinct employees Monday."}
        """;
    @BeforeEach void setup(){schedules=mock(WeeklyScheduleService.class);provider=mock(StaffingInterpreter.class);service=new StaffingInterpretationService(schedules,provider);when(schedules.findById(id)).thenReturn(new WeeklySchedule(week));}
    private InterpretationResponse interpret(String output){when(provider.interpret(eq(week),anyString())).thenReturn(output);return service.interpret(id,new InterpretationRequest("Three employees Monday working different shifts 9 AM to 5 PM"));}
    @Test void flexibleResolvesWeekdayAndLeavesUnspecifiedPoliciesUnset(){var result=interpret(valid);assertEquals(week,result.requirements().getFirst().date());assertEquals(GenerationRequest.Mode.FLEXIBLE,result.requirements().getFirst().mode());assertNull(result.maxWeeklyHours());assertNull(result.balanceHoursFairly());verify(schedules).findById(id);verifyNoMoreInteractions(schedules);}
    @Test void exactFixedShift(){var result=interpret(valid.replace("FLEXIBLE","FIXED").replace("MONDAY","TUESDAY"));assertEquals(week.plusDays(1),result.requirements().getFirst().date());assertEquals(GenerationRequest.Mode.FIXED,result.requirements().getFirst().mode());}
    @Test void multipleDaysAndExplicitDate(){var root=json.readTree(valid);var row=root.get("requirements").get(0).toString();var second=row.replace("MONDAY","2026-11-01");var result=interpret(valid.replace(row,row+","+second));assertEquals(2,result.requirements().size());assertEquals(week.plusDays(6),result.requirements().get(1).date());}
    @ParameterizedTest @ValueSource(strings={"mode","day","startTime","endTime","requiredEmployees"}) void missingFieldsAskClarificationWithoutInventing(String field){var root=(tools.jackson.databind.node.ObjectNode)json.readTree(valid);((tools.jackson.databind.node.ObjectNode)root.get("requirements").get(0)).putNull(field);var result=interpret(root.toString());assertTrue(result.requirements().isEmpty());assertFalse(result.clarificationQuestions().isEmpty());}
    @Test void continuousCoverageNeverCallsProvider(){var result=service.interpret(id,new InterpretationRequest("Three continuously on duty Monday"));assertTrue(result.requirements().isEmpty());assertFalse(result.unsupportedConstraints().isEmpty());verifyNoInteractions(provider);}
    @Test void explicitPoliciesRequireVerbatimEvidence(){String text="maximum 35 hours and balance hours fairly";String output=valid.replace("\"maxWeeklyHours\":null","\"maxWeeklyHours\":35").replace("\"maxWeeklyHoursEvidence\":null","\"maxWeeklyHoursEvidence\":\"maximum 35 hours\"").replace("\"balanceHoursFairly\":null","\"balanceHoursFairly\":true").replace("\"fairnessEvidence\":null","\"fairnessEvidence\":\"balance hours fairly\"");when(provider.interpret(week,text)).thenReturn(output);var result=service.interpret(id,new InterpretationRequest(text));assertEquals(35,result.maxWeeklyHours());assertTrue(result.balanceHoursFairly());}
    @ParameterizedTest @ValueSource(strings={"{}","not json","[]","null"}) void rejectsMalformed(String output){assertEquals(502,assertThrows(ResponseStatusException.class,()->interpret(output)).getStatusCode().value());}
    @ParameterizedTest @ValueSource(strings={"bad-day","2026-11-02","2026-10-25"}) void rejectsOutsideWeekOrInvalidDate(String day){assertEquals(502,assertThrows(ResponseStatusException.class,()->interpret(valid.replace("MONDAY",day))).getStatusCode().value());}
    @ParameterizedTest @ValueSource(strings={"-1","0","51","1.5","\"3\"","99999999999999999"}) void rejectsInvalidCount(String value){assertThrows(ResponseStatusException.class,()->interpret(valid.replace("\"requiredEmployees\":3","\"requiredEmployees\":"+value)));}
    @ParameterizedTest @ValueSource(strings={"AUTO","fixed","CONTINUOUS"}) void rejectsUnsupportedMode(String value){assertThrows(ResponseStatusException.class,()->interpret(valid.replace("FLEXIBLE",value)));}
    @Test void rejectsReversedTimeFractionalSecondsAndOverlap(){assertThrows(ResponseStatusException.class,()->interpret(valid.replace("09:00","18:00")));assertThrows(ResponseStatusException.class,()->interpret(valid.replace("09:00","09:00:00.5")));String row=json.readTree(valid).get("requirements").get(0).toString();assertThrows(ResponseStatusException.class,()->interpret(valid.replace(row,row+","+row)));}
    @Test void rejectsHallucinatedAssignmentsPromptInjectionAndTrailingJson(){assertThrows(ResponseStatusException.class,()->interpret(valid.replace("\"summary\":","\"assignments\":[],\"summary\":")));assertThrows(ResponseStatusException.class,()->interpret(valid+"{}"));assertThrows(ResponseStatusException.class,()->interpret(valid.replace("\"summary\":","\"summary\":\"injected\",\"summary\":")));verify(schedules,times(3)).findById(id);verifyNoMoreInteractions(schedules);}
    @Test void limitsRequirementCountAndTotalSlots(){String row=json.readTree(valid).get("requirements").get(0).toString();String many=String.join(",",Collections.nCopies(101,row));assertThrows(ResponseStatusException.class,()->interpret(valid.replace(row,many)));String fifty=row.replace("\"requiredEmployees\":3","\"requiredEmployees\":50");assertThrows(ResponseStatusException.class,()->interpret(valid.replace(row,String.join(",",Collections.nCopies(21,fifty)))));}
    @ParameterizedTest @ValueSource(strings={"0","169","99999999999999999","1.5","\"35\""}) void invalidWeeklyCapsRejected(String value){assertThrows(ResponseStatusException.class,()->interpret(valid.replace("\"maxWeeklyHours\":null","\"maxWeeklyHours\":"+value)));}
    @Test void rejectsInventedPolicyAndExcessiveOutput(){assertThrows(ResponseStatusException.class,()->interpret(valid.replace("\"maxWeeklyHours\":null","\"maxWeeklyHours\":40")));assertThrows(ResponseStatusException.class,()->interpret("x".repeat(32769)));}
    @Test void missingAndPublishedSchedulesDoNotCallProvider(){when(schedules.findById(id)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));assertEquals(404,assertThrows(ResponseStatusException.class,()->interpret(valid)).getStatusCode().value());var published=new WeeklySchedule(week);published.publish();doReturn(published).when(schedules).findById(id);assertEquals(409,assertThrows(ResponseStatusException.class,()->interpret(valid)).getStatusCode().value());verifyNoInteractions(provider);}
    @Test void rejectsOversizedOrBlankInstructionsBeforeDatabaseAccess(){for(String text:List.of(" ","a".repeat(4001)))assertThrows(ResponseStatusException.class,()->service.interpret(id,new InterpretationRequest(text)));verifyNoInteractions(provider,schedules);}
    @Test void providerFailuresPropagateWithoutWrites(){when(provider.interpret(eq(week),anyString())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT));assertEquals(504,assertThrows(ResponseStatusException.class,()->service.interpret(id,new InterpretationRequest("test"))).getStatusCode().value());verify(schedules).findById(id);verifyNoMoreInteractions(schedules);}
}
