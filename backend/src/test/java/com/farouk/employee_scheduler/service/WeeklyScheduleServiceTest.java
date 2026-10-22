package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.CreateWeeklyScheduleRequest;
import com.farouk.employee_scheduler.model.*;
import com.farouk.employee_scheduler.repository.ShiftRepository;
import com.farouk.employee_scheduler.repository.WeeklyScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyScheduleServiceTest {
    @Mock WeeklyScheduleRepository repository;
    @Mock ShiftRepository shiftRepository;
    @InjectMocks WeeklyScheduleService service;
    private final LocalDate monday = LocalDate.of(2026, 10, 26);

    @Test
    void createsDraftWithTimestampAndReturnsSavedId() {
        var id = UUID.randomUUID();
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            WeeklySchedule schedule = invocation.getArgument(0);
            assertEquals(ScheduleStatus.DRAFT, schedule.getStatus());
            assertNotNull(schedule.getCreatedAt());
            schedule.setId(id);
            return schedule;
        });
        var response = service.create(new CreateWeeklyScheduleRequest(monday));
        assertEquals(id, response.id());
        assertEquals(monday, response.weekStartDate());
        assertEquals(ScheduleStatus.DRAFT, response.status());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void rejectsEveryNonMonday(int offset) {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(new CreateWeeklyScheduleRequest(monday.plusDays(offset))));
        verifyNoInteractions(repository, shiftRepository);
    }

    @Test
    void rejectsNullDate() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(new CreateWeeklyScheduleRequest(null)));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10000})
    void rejectsYearsOutsideSupportedApiRange(int year) {
        LocalDate date = LocalDate.of(year, 1, 1).with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(new CreateWeeklyScheduleRequest(date)));
        verifyNoInteractions(repository);
    }

    @Test
    void duplicateWeekReturns409() {
        when(repository.existsByWeekStartDate(monday)).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT, () -> service.create(new CreateWeeklyScheduleRequest(monday)));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentUniqueViolationReturns409() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate", new SQLException("duplicate", "23505")));
        assertStatus(HttpStatus.CONFLICT, () -> service.create(new CreateWeeklyScheduleRequest(monday)));
    }

    @Test
    void unrelatedIntegrityFailureIsNotMisreportedAsDuplicate() {
        var failure = new DataIntegrityViolationException("invalid", new SQLException("invalid", "23502"));
        when(repository.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.create(new CreateWeeklyScheduleRequest(monday))));
    }

    @Test
    void listsNewestWeekFirstInRepositoryOrder() {
        when(repository.findAllByOrderByWeekStartDateDesc()).thenReturn(List.of(new WeeklySchedule(monday.plusWeeks(1)), new WeeklySchedule(monday)));
        var result = service.findAll();
        assertEquals(monday.plusWeeks(1), result.getFirst().weekStartDate());
        assertEquals(monday, result.get(1).weekStartDate());
    }

    @Test
    void emptyScheduleListIsEmpty() {
        when(repository.findAllByOrderByWeekStartDateDesc()).thenReturn(List.of());
        assertTrue(service.findAll().isEmpty());
    }

    @Test
    void missingScheduleIs404() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getDetail(UUID.randomUUID()));
        verifyNoInteractions(shiftRepository);
    }

    @Test
    void detailIncludesMappedAssignments() {
        var id = UUID.randomUUID();
        var schedule = new WeeklySchedule(monday);
        schedule.setId(id);
        var employee = new Employee();
        employee.setId(UUID.randomUUID());
        var shift = new Shift(schedule, employee, monday, LocalTime.of(9, 0), LocalTime.of(12, 0));
        shift.setId(UUID.randomUUID());
        when(repository.findById(id)).thenReturn(Optional.of(schedule));
        when(shiftRepository.findByWeeklyScheduleIdOrderByShiftDateAscStartTimeAscIdAsc(id)).thenReturn(List.of(shift));
        var response = service.getDetail(id);
        assertEquals(id, response.id());
        assertEquals(shift.getId(), response.shifts().getFirst().id());
        assertEquals(employee.getId(), response.shifts().getFirst().employeeId());
        assertEquals(monday, response.shifts().getFirst().shiftDate());
    }

    @Test
    void newScheduleDetailHasEmptyShifts() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(new WeeklySchedule(monday)));
        when(shiftRepository.findByWeeklyScheduleIdOrderByShiftDateAscStartTimeAscIdAsc(id)).thenReturn(List.of());
        assertTrue(service.getDetail(id).shifts().isEmpty());
    }

    private void assertStatus(HttpStatus status, org.junit.jupiter.api.function.Executable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action).getStatusCode());
    }
}
