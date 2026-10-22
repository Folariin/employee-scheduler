package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.GenerationRequest;
import com.farouk.employee_scheduler.dto.GenerationRequest.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.farouk.employee_scheduler.service.SchedulingAlgorithm.*;

class FlexibleSchedulingAlgorithmTest {
    private final LocalDate monday=LocalDate.of(2026,10,26);
    private final UUID a=new UUID(0,1),b=new UUID(0,2),c=new UUID(0,3);
    private EmployeeInput employee(UUID id){return new EmployeeInput(id,id.toString());}
    private List<EmployeeInput> team(){return List.of(employee(a),employee(b),employee(c));}
    private Window window(UUID id,String start,String end){return new Window(id,DayOfWeek.MONDAY,LocalTime.parse(start),LocalTime.parse(end));}
    private Busy busy(UUID id,int day,String start,String end){return new Busy(id,monday.plusDays(day),LocalTime.parse(start),LocalTime.parse(end));}
    private Requirement flexible(String start,String end,int count){return new Requirement(monday,LocalTime.parse(start),LocalTime.parse(end),count,Mode.FLEXIBLE);}
    private GenerationRequest request(Requirement requirement){return new GenerationRequest(List.of(requirement),40,true);}
    @Test void staggeredExampleUsesActualAvailabilityAndAllowsCrossEmployeeOverlap() {
        var result=generate(monday,request(flexible("09:00","17:00",3)),team(),List.of(window(a,"09:00","14:00"),window(b,"10:00","16:00"),window(c,"14:00","17:00")),List.of());
        assertEquals(3,result.assignments().size());assertEquals(0,result.coverage().getFirst().shortage());
        var times=new HashMap<UUID,String>();for(var shift:result.assignments())times.put(shift.employeeId(),shift.startTime()+"-"+shift.endTime());
        assertEquals("09:00-14:00",times.get(a));assertEquals("10:00-16:00",times.get(b));assertEquals("14:00-17:00",times.get(c));
        assertTrue(overlaps(LocalTime.of(9,0),LocalTime.of(14,0),LocalTime.of(10,0),LocalTime.of(16,0)));
        assertEquals(Mode.FLEXIBLE,result.coverage().getFirst().mode());assertTrue(result.coverage().getFirst().explanation().contains("simultaneous coverage is not guaranteed"));
    }
    @Test void clipsAvailabilityToTheWindowAndRespectsConfiguredMaximumLength() {
        var req=new GenerationRequest(List.of(flexible("09:00","17:00",1)),40,true,30,180);
        var shift=generate(monday,req,List.of(employee(a)),List.of(window(a,"07:00","20:00")),List.of()).assignments().getFirst();
        assertEquals(LocalTime.of(9,0),shift.startTime());assertEquals(LocalTime.NOON,shift.endTime());
    }
    @Test void partialAvailabilityIsEligibleInFlexibleButNotFixedMode() {
        var windows=List.of(window(a,"10:00","14:00"));
        assertEquals(1,generate(monday,request(flexible("09:00","17:00",1)),List.of(employee(a)),windows,List.of()).assignments().size());
        var fixed=new Requirement(monday,LocalTime.of(9,0),LocalTime.of(17,0),1);
        assertEquals(1,generate(monday,request(fixed),List.of(employee(a)),windows,List.of()).coverage().getFirst().shortage());
    }
    @Test void missingOutsideAndWrongDayAvailabilityNeverBecomeAllDayAvailability() {
        var windows=List.of(window(a,"06:00","08:00"),new Window(b,DayOfWeek.TUESDAY,LocalTime.of(9,0),LocalTime.of(17,0)));
        var result=generate(monday,request(flexible("09:00","17:00",3)),team(),windows,List.of());
        assertTrue(result.assignments().isEmpty());assertEquals(3,result.coverage().getFirst().shortage());
    }
    @Test void minimumLengthCausesExplicitShortageRatherThanTinyShifts() {
        var result=generate(monday,request(flexible("09:00","17:00",2)),team(),List.of(window(a,"09:00","10:59:59"),window(b,"12:00","14:00")),List.of());
        assertEquals(1,result.assignments().size());assertEquals(b,result.assignments().getFirst().employeeId());assertEquals(1,result.coverage().getFirst().shortage());
        assertTrue(result.coverage().getFirst().explanation().contains("120 minutes"));
    }
    @Test void alreadyWorkingEmployeesCountOnceAndDoNotReceiveAnotherOverlappingShift() {
        var result=generate(monday,request(flexible("09:00","17:00",2)),team(),List.of(window(a,"09:00","17:00"),window(b,"10:00","16:00")),
                List.of(busy(a,0,"09:00","10:00"),busy(a,0,"12:00","13:00")));
        assertEquals(1,result.coverage().getFirst().existingEmployees());assertEquals(1,result.assignments().size());assertEquals(b,result.assignments().getFirst().employeeId());
    }
    @Test void existingWorkersAboveTargetAreReportedAndNeverRemovedOrIncreased() {
        var result=generate(monday,request(flexible("09:00","17:00",1)),team(),List.of(window(c,"09:00","17:00")),List.of(busy(a,0,"09:00","10:00"),busy(b,0,"11:00","12:00")));
        assertTrue(result.assignments().isEmpty());assertEquals(2,result.coverage().getFirst().existingEmployees());assertTrue(result.coverage().getFirst().explanation().contains("already exceed"));
    }
    @Test void remainingWeeklyHoursTrimAValidCandidate() {
        var req=new GenerationRequest(List.of(flexible("09:00","17:00",1)),8,true);
        var shift=generate(monday,req,List.of(employee(a)),List.of(window(a,"09:00","17:00")),List.of(busy(a,1,"09:00","14:00"))).assignments().getFirst();
        assertEquals(LocalTime.NOON,shift.endTime());
    }
    @Test void remainingHoursBelowMinimumCauseShortageEvenWhenAvailabilityIsLong() {
        var req=new GenerationRequest(List.of(flexible("09:00","17:00",1)),8,true);
        var result=generate(monday,req,List.of(employee(a)),List.of(window(a,"09:00","17:00")),List.of(busy(a,1,"09:00","15:00:01")));
        assertTrue(result.assignments().isEmpty());assertEquals(1,result.coverage().getFirst().shortage());
    }
    @Test void fairnessPrefersLowerCurrentHoursBeforeLongerDuration() {
        var windows=List.of(window(a,"09:00","17:00"),window(b,"10:00","14:00"));var existing=List.of(busy(a,1,"09:00","13:00"));
        assertEquals(b,generate(monday,request(flexible("09:00","17:00",1)),team(),windows,existing).assignments().getFirst().employeeId());
        var unfair=new GenerationRequest(List.of(flexible("09:00","17:00",1)),40,false);
        assertEquals(a,generate(monday,unfair,team(),windows,existing).assignments().getFirst().employeeId());
    }
    @Test void deterministicRankingPrefersLengthThenEarliestStartThenUuid() {
        assertEquals(b,generate(monday,request(flexible("09:00","17:00",1)),team(),List.of(window(a,"09:00","13:00"),window(b,"10:00","16:00")),List.of()).assignments().getFirst().employeeId());
        assertEquals(b,generate(monday,request(flexible("09:00","17:00",1)),team(),List.of(window(a,"10:00","14:00"),window(b,"09:00","13:00")),List.of()).assignments().getFirst().employeeId());
        assertEquals(a,generate(monday,request(flexible("09:00","17:00",1)),team().reversed(),List.of(window(b,"09:00","13:00"),window(a,"09:00","13:00")),List.of()).assignments().getFirst().employeeId());
    }
    @Test void repeatedInputsIgnoreDatabaseIterationOrder() {
        var windows=List.of(window(a,"09:00","14:00"),window(b,"10:00","16:00"),window(c,"14:00","17:00"));
        var req=request(flexible("09:00","17:00",2));assertEquals(generate(monday,req,team(),windows,List.of()),generate(monday,req,team().reversed(),windows.reversed(),List.of()));
    }
    @Test void noTeamReturnsShortagesWithoutAssignments() {
        var result=generate(monday,request(flexible("09:00","17:00",3)),List.of(),List.of(),List.of());
        assertTrue(result.assignments().isEmpty());assertEquals(3,result.coverage().getFirst().shortage());
    }
    @Test void neverSelectsMoreDistinctWorkersThanRequested() {
        var result=generate(monday,request(flexible("09:00","17:00",2)),team(),List.of(window(a,"09:00","17:00"),window(b,"09:00","17:00"),window(c,"09:00","17:00")),List.of());
        assertEquals(2,result.assignments().size());assertEquals(2,result.assignments().stream().map(s->s.employeeId()).distinct().count());
    }
    @Test void mixedModesShareTheSameWeeklyHourBudgetWithoutChangingFixedTimes() {
        var req=new GenerationRequest(List.of(new Requirement(monday,LocalTime.of(9,0),LocalTime.of(10,0),1),
                new Requirement(monday.plusDays(1),LocalTime.of(9,0),LocalTime.of(17,0),1,Mode.FLEXIBLE)),8,true);
        var result=generate(monday,req,List.of(employee(a)),List.of(window(a,"09:00","17:00"),new Window(a,DayOfWeek.TUESDAY,LocalTime.of(9,0),LocalTime.of(17,0))),List.of());
        assertEquals(LocalTime.of(10,0),result.assignments().get(0).endTime());assertEquals(LocalTime.of(16,0),result.assignments().get(1).endTime());
        assertEquals("8.00",result.workloads().getFirst().resultingWeeklyHours().toString());
    }
    @Test void adjacentFlexibleRequirementsCanReuseAnEmployeeWithoutOverlap() {
        var req=new GenerationRequest(List.of(flexible("09:00","12:00",1),flexible("12:00","17:00",1)),40,true);
        var result=generate(monday,req,List.of(employee(a)),List.of(window(a,"09:00","17:00")),List.of());
        assertEquals(2,result.assignments().size());assertEquals(result.assignments().get(0).endTime(),result.assignments().get(1).startTime());
    }
    @Test void fractionalAvailabilityRoundsInwardAndNeverCreatesUnpersistableTimes() {
        var result=generate(monday,request(flexible("09:00","17:00",1)),List.of(employee(a)),List.of(window(a,"09:00:00.1","11:00:01.7")),List.of());
        var shift=result.assignments().getFirst();assertEquals(LocalTime.of(9,0,1),shift.startTime());assertEquals(LocalTime.of(11,0,1),shift.endTime());
    }
    @Test void roundingCannotSilentlyUndercutTheMinimum() {
        var result=generate(monday,request(flexible("09:00","17:00",1)),List.of(employee(a)),List.of(window(a,"09:00:00.1","11:00:00.9")),List.of());assertTrue(result.assignments().isEmpty());
    }
    @Test void fixedIntervalsIgnoreFlexibleDurationDefaults() {
        var req=new GenerationRequest(List.of(new Requirement(monday,LocalTime.of(9,0),LocalTime.of(9,30),1,Mode.FIXED)),40,true);
        assertEquals(LocalTime.of(9,30),generate(monday,req,List.of(employee(a)),List.of(window(a,"09:00","17:00")),List.of()).assignments().getFirst().endTime());
    }
    @ParameterizedTest @CsvSource({"0,480","120,0","181,180","120,1441","-1,480"})
    void invalidDurationLimitsAreRejected(int min,int max) {
        var request=new GenerationRequest(List.of(flexible("09:00","17:00",1)),40,true,min,max);
        assertThrows(ResponseStatusException.class,()->validate(monday,request));
    }
    @Test void oldJavaAndOmittedModeContractsRemainFixed() {
        var requirement=new Requirement(monday,LocalTime.of(9,0),LocalTime.of(17,0),1,null);
        assertEquals(Mode.FIXED,requirement.mode());assertEquals(120,request(requirement).effectiveMinShiftMinutes());assertEquals(480,request(requirement).effectiveMaxShiftMinutes());
    }
}
