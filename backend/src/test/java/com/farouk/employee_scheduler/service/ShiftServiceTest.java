package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.ShiftRequest;
import com.farouk.employee_scheduler.model.*;
import com.farouk.employee_scheduler.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.sql.SQLException;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {
    @Mock ShiftRepository shifts;
    @Mock EmployeeRepository employees;
    @Mock AvailabilityRepository availability;
    @Mock WeeklyScheduleService schedules;
    @InjectMocks ShiftService service;
    private final UUID scheduleId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID shiftId = UUID.randomUUID();
    private final LocalDate monday = LocalDate.of(2026, 10, 26);
    private WeeklySchedule schedule;
    private Employee employee;

    @BeforeEach
    void setUp() {
        schedule = new WeeklySchedule(monday);
        schedule.setId(scheduleId);
        employee = new Employee();
        employee.setId(employeeId);
    }

    private ShiftRequest request(String start, String end) {
        return new ShiftRequest(employeeId, monday, LocalTime.parse(start), LocalTime.parse(end));
    }

    private void scheduleExists() { when(schedules.findById(scheduleId)).thenReturn(schedule); }

    private void employeeAvailable() {
        when(employees.findById(employeeId)).thenReturn(Optional.of(employee));
        when(availability.findByEmployee_IdAndDayOfWeek(employeeId, DayOfWeek.MONDAY)).thenReturn(Optional.of(
                new Availability(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), employee)));
    }

    private void saveReturnsAssignedId() {
        when(shifts.saveAndFlush(any())).thenAnswer(invocation -> {
            Shift shift = invocation.getArgument(0);
            shift.setId(shiftId);
            return shift;
        });
    }

    @ParameterizedTest
    @CsvSource({"10:00,16:00", "09:00,17:00", "09:00,09:00:01", "16:59:59,17:00"})
    void createsCoveredShiftIncludingAvailabilityBoundaries(String start, String end) {
        scheduleExists(); employeeAvailable(); saveReturnsAssignedId();
        var response = service.create(scheduleId, request(start, end));
        assertEquals(shiftId, response.id());
        assertEquals(employeeId, response.employeeId());
        assertEquals(scheduleId, response.scheduleId());
        assertEquals(monday, response.shiftDate());
        verify(shifts).existsOverlapping(employeeId, monday, LocalTime.parse(start), LocalTime.parse(end), null);
    }

    @ParameterizedTest
    @CsvSource({"09:00,09:00", "17:00,09:00", "23:00,01:00", "09:00:00.1,10:00", "09:00,10:00:00.1"})
    void invalidAndOvernightOrFractionalRangesAre400(String start, String end) {
        scheduleExists();
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(scheduleId, request(start, end)));
        verifyNoInteractions(employees, availability, shifts);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 7, 14})
    void dateOutsideWeekIs400(int offset) {
        scheduleExists();
        var request = new ShiftRequest(employeeId, monday.plusDays(offset), LocalTime.of(9, 0), LocalTime.of(12, 0));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(scheduleId, request));
        verifyNoInteractions(employees, availability, shifts);
    }

    @Test
    void missingScheduleIs404() {
        when(schedules.findById(scheduleId)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.create(scheduleId, request("10:00", "16:00")));
        verifyNoInteractions(employees, availability, shifts);
    }

    @Test
    void missingEmployeeIs404() {
        scheduleExists();
        assertStatus(HttpStatus.NOT_FOUND, () -> service.create(scheduleId, request("10:00", "16:00")));
        verifyNoInteractions(availability, shifts);
    }

    @Test
    void missingRecurringAvailabilityIs409() {
        scheduleExists();
        when(employees.findById(employeeId)).thenReturn(Optional.of(employee));
        assertStatus(HttpStatus.CONFLICT, () -> service.create(scheduleId, request("10:00", "16:00")));
        verifyNoInteractions(shifts);
    }

    @ParameterizedTest
    @CsvSource({"08:00,12:00", "16:00,18:00", "17:00,20:00", "06:00,08:00", "08:00,18:00"})
    void partiallyOrCompletelyOutsideAvailabilityIs409(String start, String end) {
        scheduleExists(); employeeAvailable();
        assertStatus(HttpStatus.CONFLICT, () -> service.create(scheduleId, request(start, end)));
        verifyNoInteractions(shifts);
    }

    @Test
    void existingOverlapIs409WithoutSaving() {
        scheduleExists(); employeeAvailable();
        when(shifts.existsOverlapping(employeeId, monday, LocalTime.of(10, 0), LocalTime.of(16, 0), null)).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT, () -> service.create(scheduleId, request("10:00", "16:00")));
        verify(shifts, never()).saveAndFlush(any());
    }

    @Test
    void databaseExclusionViolationIs409() {
        scheduleExists(); employeeAvailable();
        when(shifts.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("conflict", new SQLException("overlap", "23P01")));
        assertStatus(HttpStatus.CONFLICT, () -> service.create(scheduleId, request("10:00", "16:00")));
    }

    @Test
    void databaseDeadlockIsReportedAsRetryableConflict() {
        scheduleExists(); employeeAvailable();
        when(shifts.saveAndFlush(any())).thenThrow(new CannotAcquireLockException("deadlock"));
        assertStatus(HttpStatus.CONFLICT, () -> service.create(scheduleId, request("10:00", "16:00")));
    }

    @Test
    void unrelatedIntegrityFailureIsPreserved() {
        scheduleExists(); employeeAvailable();
        var failure = new DataIntegrityViolationException("other", new SQLException("other", "23503"));
        when(shifts.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.create(scheduleId, request("10:00", "16:00"))));
    }

    @Test
    void updateExcludesItselfAndPreservesScheduleAndId() {
        employeeAvailable(); saveReturnsAssignedId();
        var shift = new Shift(schedule, employee, monday, LocalTime.of(9, 0), LocalTime.of(12, 0));
        shift.setId(shiftId);
        when(shifts.findByIdForUpdate(shiftId)).thenReturn(Optional.of(shift));
        var response = service.update(shiftId, request("10:00", "16:00"));
        verify(shifts).existsOverlapping(employeeId, monday, LocalTime.of(10, 0), LocalTime.of(16, 0), shiftId);
        assertEquals(shiftId, response.id());
        assertEquals(scheduleId, response.scheduleId());
        assertEquals(LocalTime.of(16, 0), shift.getEndTime());
    }

    @Test
    void updateReassignsEmployeeAndWeekdayWithSameRules() {
        var other = new Employee(); other.setId(UUID.randomUUID());
        var shift = new Shift(schedule, other, monday, LocalTime.of(9, 0), LocalTime.of(12, 0)); shift.setId(shiftId);
        when(shifts.findByIdForUpdate(shiftId)).thenReturn(Optional.of(shift));
        when(employees.findById(employeeId)).thenReturn(Optional.of(employee));
        when(availability.findByEmployee_IdAndDayOfWeek(employeeId, DayOfWeek.SUNDAY)).thenReturn(Optional.of(
                new Availability(DayOfWeek.SUNDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), employee)));
        saveReturnsAssignedId();
        var response = service.update(shiftId, new ShiftRequest(employeeId, monday.plusDays(6), LocalTime.of(10, 0), LocalTime.of(16, 0)));
        assertEquals(employeeId, response.employeeId());
        assertEquals(monday.plusDays(6), response.shiftDate());
    }

    @Test
    void rejectedUpdateDoesNotMutateExistingAssignment() {
        employeeAvailable();
        var shift = new Shift(schedule, employee, monday, LocalTime.of(9, 0), LocalTime.of(12, 0)); shift.setId(shiftId);
        when(shifts.findByIdForUpdate(shiftId)).thenReturn(Optional.of(shift));
        assertStatus(HttpStatus.CONFLICT, () -> service.update(shiftId, request("08:00", "12:00")));
        assertEquals(LocalTime.of(9, 0), shift.getStartTime());
        verify(shifts, never()).saveAndFlush(any());
    }

    @Test
    void missingUpdateDeleteAndLookupAre404() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.update(shiftId, request("10:00", "16:00")));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.delete(shiftId));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.findById(shiftId));
        verify(shifts, never()).saveAndFlush(any());
        verify(shifts, never()).delete(any());
    }

    @Test
    void deletesOnlyShiftAndFlushes() {
        var shift = new Shift(schedule, employee, monday, LocalTime.of(9, 0), LocalTime.of(12, 0));
        when(shifts.findByIdForUpdate(shiftId)).thenReturn(Optional.of(shift));
        service.delete(shiftId);
        verify(shifts).delete(shift); verify(shifts).flush();
        verifyNoInteractions(employees, availability, schedules);
    }

    @Test
    void cannotCreateUpdateOrDeleteOutsideDraft() {
        var unsupported = mock(WeeklySchedule.class);
        when(unsupported.getStatus()).thenReturn(null);
        when(schedules.findById(scheduleId)).thenReturn(unsupported);
        var shift = new Shift(); shift.setWeeklySchedule(unsupported);
        when(shifts.findByIdForUpdate(shiftId)).thenReturn(Optional.of(shift));
        assertStatus(HttpStatus.CONFLICT, () -> service.create(scheduleId, request("10:00", "16:00")));
        assertStatus(HttpStatus.CONFLICT, () -> service.update(shiftId, request("10:00", "16:00")));
        assertStatus(HttpStatus.CONFLICT, () -> service.delete(shiftId));
    }

    private void assertStatus(HttpStatus status, org.junit.jupiter.api.function.Executable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action).getStatusCode());
    }
}
