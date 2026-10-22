package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.*;
import com.farouk.employee_scheduler.model.*;
import com.farouk.employee_scheduler.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutomaticSchedulingServiceTest {
    private final WeeklyScheduleService schedules=mock(WeeklyScheduleService.class);
    private final EmployeeRepository employees=mock(EmployeeRepository.class);
    private final AvailabilityRepository availability=mock(AvailabilityRepository.class);
    private final ShiftRepository shifts=mock(ShiftRepository.class);
    private final ShiftService shiftService=mock(ShiftService.class);
    private final GenerationPreviewStore store=new GenerationPreviewStore();
    private final AutomaticSchedulingService service=new AutomaticSchedulingService(schedules,employees,availability,shifts,shiftService,store);
    private final WeeklySchedule schedule=new WeeklySchedule(LocalDate.of(2026,10,26));
    private final UUID scheduleId=UUID.randomUUID(),employeeId=UUID.randomUUID();
    private final GenerationRequest request=new GenerationRequest(List.of(new GenerationRequest.Requirement(LocalDate.of(2026,10,26),LocalTime.of(9,0),LocalTime.of(17,0),1)),40,true);
    @BeforeEach void fixture() {
        schedule.setId(scheduleId);when(schedules.findById(scheduleId)).thenReturn(schedule);when(schedules.lockForModification(scheduleId)).thenReturn(schedule);
        var employee=new Employee();employee.setId(employeeId);
        var window=new Availability(DayOfWeek.MONDAY,LocalTime.of(9,0),LocalTime.of(17,0),employee);
        when(employees.findAll()).thenReturn(List.of(employee));when(employees.findAllLockedForScheduling()).thenReturn(List.of(employee));
        when(availability.findAll()).thenReturn(List.of(window));when(availability.findAllLockedForScheduling()).thenReturn(List.of(window));
    }
    @Test void previewCallsNoMutationServicesAndContainsNoSensitiveFields() {
        var preview=service.preview(scheduleId,request,"admin");assertEquals(1,preview.assignments().size());
        verifyNoInteractions(shiftService);verify(schedules,never()).lockForModification(any());
        verify(shifts,never()).save(any());verify(availability,never()).findAllLockedForScheduling();
    }
    @Test void applyUsesOnlyServerStoredAssignmentsAndConsumesToken() {
        var preview=service.preview(scheduleId,request,"admin");var selected=List.of(preview.assignments().getFirst().suggestionId());
        when(shiftService.create(eq(scheduleId),any())).thenReturn(new ShiftResponse(UUID.randomUUID(),scheduleId,employeeId,request.requirements().getFirst().date(),LocalTime.of(9,0),LocalTime.of(17,0)));
        assertEquals(1,service.apply(scheduleId,new ApplyGenerationRequest(preview.previewToken(),selected),"admin").size());
        verify(shiftService).create(eq(scheduleId),eq(new ShiftRequest(employeeId,request.requirements().getFirst().date(),LocalTime.of(9,0),LocalTime.of(17,0))));
        assertThrows(ResponseStatusException.class,()->service.apply(scheduleId,new ApplyGenerationRequest(preview.previewToken(),selected),"admin"));
    }
    @Test void staleAvailabilityRejectsBeforeSaving() {
        var preview=service.preview(scheduleId,request,"admin");when(availability.findAllLockedForScheduling()).thenReturn(List.of());
        var error=assertThrows(ResponseStatusException.class,()->service.apply(scheduleId,new ApplyGenerationRequest(preview.previewToken(),List.of(preview.assignments().getFirst().suggestionId())),"admin"));
        assertEquals(409,error.getStatusCode().value());verifyNoInteractions(shiftService);
    }
    @Test void foreignOwnerAndTamperedSelectionAreRejected() {
        var preview=service.preview(scheduleId,request,"admin");
        assertThrows(ResponseStatusException.class,()->service.apply(scheduleId,new ApplyGenerationRequest(preview.previewToken(),List.of("fake")),"another-admin"));
        var error=assertThrows(ResponseStatusException.class,()->service.apply(scheduleId,new ApplyGenerationRequest(preview.previewToken(),List.of("fake")),"admin"));
        assertEquals(400,error.getStatusCode().value());verifyNoInteractions(shiftService);
    }
    @Test void duplicateOrEmptySelectionsAreBadRequests() {
        var preview=service.preview(scheduleId,request,"admin");String id=preview.assignments().getFirst().suggestionId();
        for(var ids:List.of(List.of(id,id),List.<String>of()))assertThrows(ResponseStatusException.class,()->service.apply(scheduleId,new ApplyGenerationRequest(preview.previewToken(),ids),"admin"));
        verifyNoInteractions(shiftService);
    }
    @Test void publishedScheduleRejectsPreviewAndApplyAtTheServiceLayer() {
        schedule.publish();
        assertThrows(ResponseStatusException.class,()->service.preview(scheduleId,request,"admin"));
        assertThrows(ResponseStatusException.class,()->service.apply(scheduleId,new ApplyGenerationRequest(UUID.randomUUID(),List.of("fake")),"admin"));
        verifyNoInteractions(shiftService);
    }
    @Test void flexibleDurationOptionsRemainBoundToTheStoredPreviewAndApplyTimes() {
        var flexible=new GenerationRequest(List.of(new GenerationRequest.Requirement(request.requirements().getFirst().date(),LocalTime.of(9,0),LocalTime.of(17,0),1,GenerationRequest.Mode.FLEXIBLE)),40,true,30,60);
        var preview=service.preview(scheduleId,flexible,"admin");
        assertEquals(60,store.get(preview.previewToken(),scheduleId,"admin").request().effectiveMaxShiftMinutes());
        when(shiftService.create(eq(scheduleId),any())).thenReturn(new ShiftResponse(UUID.randomUUID(),scheduleId,employeeId,request.requirements().getFirst().date(),LocalTime.of(9,0),LocalTime.of(10,0)));
        service.apply(scheduleId,new ApplyGenerationRequest(preview.previewToken(),List.of(preview.assignments().getFirst().suggestionId())),"admin");
        verify(shiftService).create(scheduleId,new ShiftRequest(employeeId,request.requirements().getFirst().date(),LocalTime.of(9,0),LocalTime.of(10,0)));
    }
}
