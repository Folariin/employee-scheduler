package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.GenerationRequest;
import com.farouk.employee_scheduler.dto.GenerationRequest.Requirement;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.farouk.employee_scheduler.service.SchedulingAlgorithm.*;

class SchedulingAlgorithmTest {
    private final LocalDate monday=LocalDate.of(2026,10,26);
    private final UUID alice=new UUID(0,1),bob=new UUID(0,2);
    private List<EmployeeInput> team(){return List.of(new EmployeeInput(bob,"Bob"),new EmployeeInput(alice,"Alice"));}
    private Window window(UUID id,DayOfWeek day,String start,String end){return new Window(id,day,LocalTime.parse(start),LocalTime.parse(end));}
    private List<Window> windows(){var result=new ArrayList<Window>();for(UUID id:List.of(alice,bob))for(var day:DayOfWeek.values())result.add(window(id,day,"09:00","17:00"));return result;}
    private Requirement requirement(int day,String start,String end,int count){return new Requirement(monday.plusDays(day),LocalTime.parse(start),LocalTime.parse(end),count);}
    private GenerationRequest request(Requirement... requirements){return new GenerationRequest(List.of(requirements),40,true);}
    private Busy busy(UUID id,int day,String start,String end){return new Busy(id,monday.plusDays(day),LocalTime.parse(start),LocalTime.parse(end));}
    @Test void validAssignmentUsesDeterministicEmployeeOrderAndOnlyDisplayData() {
        var result=generate(monday,request(requirement(0,"09:00","17:00",1)),team(),windows(),List.of());
        assertEquals(alice,result.assignments().getFirst().employeeId());assertEquals("Alice",result.assignments().getFirst().displayName());
        assertEquals(0,result.coverage().getFirst().shortage());assertEquals("8.00",result.workloads().getFirst().resultingWeeklyHours().toString());
    }
    @Test void missingDaysAndPartialAvailabilityAreIneligible() {
        var result=generate(monday,request(requirement(0,"09:00","17:00",2)),team(),
                List.of(window(alice,DayOfWeek.TUESDAY,"09:00","17:00"),window(bob,DayOfWeek.MONDAY,"10:00","17:00")),List.of());
        assertTrue(result.assignments().isEmpty());assertEquals(2,result.coverage().getFirst().shortage());
    }
    @Test void existingOverlapExcludesEmployeeAndReportsPartialCoverage() {
        var result=generate(monday,request(requirement(0,"09:00","17:00",2)),team(),windows(),List.of(busy(alice,0,"10:00","12:00")));
        assertEquals(bob,result.assignments().getFirst().employeeId());assertEquals(1,result.coverage().getFirst().shortage());
        assertFalse(result.coverage().getFirst().explanation().isBlank());
    }
    @Test void adjacentExistingShiftsAreAllowed() {
        var result=generate(monday,request(requirement(0,"12:00","17:00",2)),team(),windows(),List.of(busy(alice,0,"09:00","12:00")));
        assertEquals(2,result.assignments().size());assertEquals(0,result.coverage().getFirst().shortage());
    }
    @Test void existingFullyCoveringAssignmentsCountTowardTargetWithoutDuplicates() {
        var result=generate(monday,request(requirement(0,"10:00","16:00",1)),team(),windows(),List.of(busy(alice,0,"09:00","17:00")));
        assertTrue(result.assignments().isEmpty());assertEquals(1,result.coverage().getFirst().existingEmployees());assertEquals(0,result.coverage().getFirst().shortage());
    }
    @Test void adjacentExistingRecordsTogetherCoverARequirementWithoutOverstaffing() {
        var result=generate(monday,request(requirement(0,"09:00","17:00",1)),team(),windows(),List.of(busy(alice,0,"09:00","12:00"),busy(alice,0,"12:00","17:00")));
        assertTrue(result.assignments().isEmpty());assertEquals(1,result.coverage().getFirst().existingEmployees());
    }
    @Test void aGapInExistingRecordsDoesNotCountAsFullCoverage() {
        var result=generate(monday,request(requirement(0,"09:00","17:00",1)),team(),windows(),List.of(busy(alice,0,"09:00","12:00"),busy(alice,0,"13:00","17:00")));
        assertEquals(bob,result.assignments().getFirst().employeeId());assertEquals(0,result.coverage().getFirst().existingEmployees());
    }
    @Test void existingHoursAndProposedHoursBothEnforceLimit() {
        var req=new GenerationRequest(List.of(requirement(1,"09:00","17:00",2),requirement(2,"09:00","17:00",2)),8,true);
        var result=generate(monday,req,team(),windows(),List.of(busy(alice,0,"09:00","17:00")));
        assertEquals(1,result.assignments().size());assertEquals(bob,result.assignments().getFirst().employeeId());
        assertEquals(List.of(1,2),result.coverage().stream().map(c->c.shortage()).toList());
    }
    @Test void fairnessPrefersFewerHoursAndBalancesMultipleRequirements() {
        var result=generate(monday,request(requirement(1,"09:00","17:00",1),requirement(2,"09:00","17:00",1),requirement(3,"09:00","17:00",1)),team(),windows(),List.of(busy(alice,0,"09:00","17:00")));
        assertEquals(List.of(bob,alice,bob),result.assignments().stream().map(a->a.employeeId()).toList());
    }
    @Test void fairnessOffStillUsesStableIdOrderAndDoesNotBypassConstraints() {
        var req=new GenerationRequest(List.of(requirement(1,"09:00","17:00",1)),40,false);
        assertEquals(alice,generate(monday,req,team(),windows(),List.of(busy(alice,0,"09:00","17:00"))).assignments().getFirst().employeeId());
    }
    @Test void inputDatabaseOrderingDoesNotChangeResults() {
        var req=request(requirement(2,"13:00","17:00",2),requirement(0,"09:00","17:00",1));
        var first=generate(monday,req,team(),windows(),List.of());var reversed=new ArrayList<>(windows());Collections.reverse(reversed);
        assertEquals(first,generate(monday,req,team().reversed(),reversed,List.of()));
        assertEquals(monday,first.assignments().getFirst().shiftDate());
    }
    @Test void demandBeyondTeamReturnsExplicitShortage() {
        var result=generate(monday,request(requirement(0,"09:00","17:00",5)),team(),windows(),List.of());
        assertEquals(2,result.assignments().size());assertEquals(3,result.coverage().getFirst().shortage());
    }
    @Test void emptyTeamReturnsEmptyAssignmentsAndCompleteShortage() {
        var result=generate(monday,request(requirement(0,"09:00","17:00",3)),List.of(),List.of(),List.of());
        assertTrue(result.assignments().isEmpty());assertTrue(result.workloads().isEmpty());assertEquals(3,result.coverage().getFirst().shortage());
    }
    @Test void adjacentRequirementsCanAssignSameEmployeeWithoutOverlap() {
        var result=generate(monday,request(requirement(0,"09:00","12:00",2),requirement(0,"12:00","17:00",2)),team(),windows(),List.of());
        assertEquals(4,result.assignments().size());
    }
    @Test void overlappingAndDuplicateRequirementsAreRejected() {
        for(var second:List.of(requirement(0,"11:00","15:00",1),requirement(0,"09:00","12:00",1)))
            assertThrows(ResponseStatusException.class,()->generate(monday,request(requirement(0,"09:00","12:00",1),second),team(),windows(),List.of()));
    }
    @Test void secondsAreNotRoundedForTheHardHoursLimit() {
        var req=new GenerationRequest(List.of(requirement(0,"10:00","10:00:01",2)),1,true);
        var result=generate(monday,req,team(),windows(),List.of(busy(alice,0,"09:00","10:00")));
        assertEquals(1,result.assignments().size());assertEquals(bob,result.assignments().getFirst().employeeId());
    }
    @ParameterizedTest @ValueSource(ints={-1,7}) void outsideWeekRejected(int day) {
        assertThrows(ResponseStatusException.class,()->validate(monday,request(requirement(day,"09:00","17:00",1))));
    }
    @ParameterizedTest @ValueSource(ints={0,51,-1}) void invalidHeadcountRejected(int count) {
        assertThrows(ResponseStatusException.class,()->validate(monday,request(requirement(0,"09:00","17:00",count))));
    }
    @ParameterizedTest @ValueSource(ints={0,169,-1}) void invalidHourLimitRejected(int hours) {
        assertThrows(ResponseStatusException.class,()->validate(monday,new GenerationRequest(List.of(requirement(0,"09:00","17:00",1)),hours,true)));
    }
    @Test void invalidTimesAndFractionalSecondsAreRejected() {
        for(var start:List.of("17:00","18:00","09:00:00.1"))
            assertThrows(ResponseStatusException.class,()->validate(monday,request(requirement(0,start,"17:00",1))));
    }
    @Test void emptyNullAndOversizedRequestsAreRejected() {
        assertThrows(ResponseStatusException.class,()->validate(monday,null));
        assertThrows(ResponseStatusException.class,()->validate(monday,new GenerationRequest(null,40,true)));
        assertThrows(ResponseStatusException.class,()->validate(monday,new GenerationRequest(List.of(),40,true)));
        assertThrows(ResponseStatusException.class,()->validate(monday,new GenerationRequest(Collections.nCopies(101,requirement(0,"09:00","17:00",1)),40,true)));
        assertThrows(ResponseStatusException.class,()->validate(monday,new GenerationRequest(Collections.singletonList(null),40,true)));
    }
    @Test void totalRequestedAssignmentsAreBoundedEvenForNonoverlappingRequirements() {
        var rows=new ArrayList<Requirement>();
        for(int i=0;i<21;i++)rows.add(new Requirement(monday,LocalTime.of(9,0).plusMinutes(i*5),LocalTime.of(9,0).plusMinutes((i+1)*5),50));
        var error=assertThrows(ResponseStatusException.class,()->validate(monday,new GenerationRequest(rows,40,true)));
        assertTrue(error.getReason().contains("1000"));
    }
}
