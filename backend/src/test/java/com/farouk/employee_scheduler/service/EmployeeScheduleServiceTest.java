package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.model.*;
import com.farouk.employee_scheduler.repository.ShiftRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeScheduleServiceTest {
    ShiftRepository shifts = mock(ShiftRepository.class);
    EmployeeService employees = mock(EmployeeService.class);
    EmployeeScheduleService service = new EmployeeScheduleService(shifts, employees);
    String userId = UUID.randomUUID().toString();
    Employee employee = new Employee();
    LocalDate monday = LocalDate.of(2026, 10, 26);

    @Test void resolvesIdentityAndPassesPublishedStatusAndInclusiveBoundsToRepository() {
        employee.setId(UUID.randomUUID());
        when(employees.findByUserId(userId)).thenReturn(employee);
        assertTrue(service.getMyShifts(userId, monday, monday).isEmpty());
        verify(shifts).findVisibleShifts(employee.getId(), ScheduleStatus.PUBLISHED, monday, monday);
    }
    @ParameterizedTest @CsvSource({",", "2026-10-26,", ",2026-10-26"})
    void optionalBoundsUseSupportedCalendarLimits(String start, String end) {
        employee.setId(UUID.randomUUID()); when(employees.findByUserId(userId)).thenReturn(employee);
        LocalDate first = start == null ? null : LocalDate.parse(start);
        LocalDate last = end == null ? null : LocalDate.parse(end);
        assertTrue(service.getMySchedules(userId, first, last).isEmpty());
        verify(shifts).findVisibleShifts(employee.getId(), ScheduleStatus.PUBLISHED,
                first == null ? LocalDate.of(1,1,1) : first, last == null ? LocalDate.of(9999,12,31) : last);
    }
    @ParameterizedTest @CsvSource({"2026-10-27,2026-10-26", "0000-01-01,2026-10-26", "2026-10-26,+10000-01-01"})
    void invalidRangesAre400BeforeQuerying(String start, String end) {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.getMyShifts(userId, LocalDate.parse(start), LocalDate.parse(end))).getStatusCode().value());
        verifyNoInteractions(employees, shifts);
    }
    @Test void groupsOnlyReturnedAssignmentsIntoExistingCalendarDtos() {
        employee.setId(UUID.randomUUID()); when(employees.findByUserId(userId)).thenReturn(employee);
        WeeklySchedule week = new WeeklySchedule(monday); week.setId(UUID.randomUUID()); week.publish();
        WeeklySchedule next = new WeeklySchedule(monday.plusDays(7)); next.setId(UUID.randomUUID()); next.publish();
        Shift a = new Shift(week, employee, monday, LocalTime.of(9,0), LocalTime.NOON); a.setId(UUID.randomUUID());
        Shift b = new Shift(week, employee, monday.plusDays(1), LocalTime.NOON, LocalTime.of(13,0)); b.setId(UUID.randomUUID());
        Shift c = new Shift(next, employee, monday.plusDays(7), LocalTime.NOON, LocalTime.of(13,0)); c.setId(UUID.randomUUID());
        when(shifts.findVisibleShifts(any(), any(), any(), any())).thenReturn(List.of(a,b,c));
        var result = service.getMySchedules(userId, null, null);
        assertEquals(2, result.size()); assertEquals(2, result.getFirst().shifts().size());
        assertEquals(week.getId(), result.getFirst().id()); assertEquals(next.getId(), result.getLast().id());
        assertEquals(employee.getId(), result.getLast().shifts().getFirst().employeeId());
        assertEquals(ScheduleStatus.PUBLISHED, result.getFirst().status());
    }
    @Test void missingEmployeeProfilePreservesExisting404() {
        when(employees.findByUserId(userId)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.getMySchedules(userId,null,null)).getStatusCode().value());
        verifyNoInteractions(shifts);
    }
}
