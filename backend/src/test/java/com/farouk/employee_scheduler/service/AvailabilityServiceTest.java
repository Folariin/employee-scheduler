package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.AvailabilityResponse;
import com.farouk.employee_scheduler.model.Availability;
import com.farouk.employee_scheduler.model.Employee;
import com.farouk.employee_scheduler.repository.AvailabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {
    @Mock AvailabilityRepository repository;
    @InjectMocks AvailabilityService service;
    private Employee employee;
    private Availability availability;
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(employeeId);
        availability = new Availability(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), employee);
    }

    @Test
    void createsAndReturnsPersistedAvailability() {
        var saved = new Availability();
        when(repository.saveAndFlush(availability)).thenReturn(saved);
        assertSame(saved, service.save(availability));
        verify(repository).existsByEmployee_IdAndDayOfWeek(employeeId, DayOfWeek.MONDAY);
        verify(repository).saveAndFlush(availability);
    }

    @Test
    void duplicateDayReturns409WithoutInserting() {
        when(repository.existsByEmployee_IdAndDayOfWeek(employeeId, DayOfWeek.MONDAY)).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT, () -> service.save(availability));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentUniqueViolationReturns409() {
        var failure = new DataIntegrityViolationException("duplicate", new SQLException("duplicate", "23505"));
        when(repository.saveAndFlush(availability)).thenThrow(failure);
        var exception = assertThrows(ResponseStatusException.class, () -> service.save(availability));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertSame(failure, exception.getCause());
    }

    @ParameterizedTest
    @CsvSource({"23502", "23503", "23514"})
    void unrelatedIntegrityFailuresAreNotReportedAsDuplicateDay(String sqlState) {
        var failure = new DataIntegrityViolationException("integrity failure", new SQLException("failure", sqlState));
        when(repository.saveAndFlush(availability)).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.save(availability)));
    }

    @Test
    void integrityFailureWithoutSqlCauseIsPreserved() {
        var failure = new DataIntegrityViolationException("unknown failure");
        when(repository.saveAndFlush(availability)).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.save(availability)));
    }

    @ParameterizedTest
    @CsvSource(value = {"09:00,09:00", "17:00,09:00", "23:00,01:00", "NULL,17:00", "09:00,NULL"}, nullValues = "NULL")
    void rejectsInvalidCreateAndUpdateRangesBeforeRepositoryAccess(String start, String end) {
        LocalTime startTime = start == null ? null : LocalTime.parse(start);
        LocalTime endTime = end == null ? null : LocalTime.parse(end);
        availability.setStartTime(startTime);
        availability.setEndTime(endTime);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.save(availability));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.updateAvailability(employeeId, DayOfWeek.MONDAY, startTime, endTime));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMissingDayBeforeSaving() {
        availability.setDayOfWeek(null);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.save(availability));
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsMidnightAndShortSameDayIntervals() {
        availability.setStartTime(LocalTime.MIDNIGHT);
        availability.setEndTime(LocalTime.of(0, 0, 1));
        when(repository.saveAndFlush(availability)).thenReturn(availability);
        assertSame(availability, service.save(availability));
    }

    @Test
    void retrievesOnlyRequestedEmployeeAvailability() {
        when(repository.findByEmployeeId(employeeId)).thenReturn(List.of(availability));
        assertEquals(List.of(availability), service.findByEmployeeId(employeeId));
        verify(repository).findByEmployeeId(employeeId);
        verify(repository, never()).findAll();
    }

    @Test
    void emptyEmployeeAvailabilityReturnsEmptyList() {
        when(repository.findByEmployeeId(employeeId)).thenReturn(List.of());
        assertTrue(service.findByEmployeeId(employeeId).isEmpty());
    }

    @Test
    void findsExistingAvailabilityById() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(availability));
        assertSame(availability, service.findById(id));
    }

    @Test
    void missingAvailabilityByIdReturns404() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.findById(UUID.randomUUID()));
    }

    @Test
    void updatesOnlyEmployeeAndDayAndPreservesIdentity() {
        when(repository.findByEmployee_IdAndDayOfWeek(employeeId, DayOfWeek.MONDAY)).thenReturn(Optional.of(availability));
        when(repository.save(availability)).thenReturn(availability);
        assertSame(availability, service.updateAvailability(employeeId, DayOfWeek.MONDAY, LocalTime.NOON, LocalTime.of(18, 0)));
        assertEquals(LocalTime.NOON, availability.getStartTime());
        assertEquals(LocalTime.of(18, 0), availability.getEndTime());
        assertEquals(DayOfWeek.MONDAY, availability.getDayOfWeek());
        assertSame(employee, availability.getEmployee());
        verify(repository).save(availability);
    }

    @Test
    void missingUpdateReturns404WithoutInserting() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.updateAvailability(employeeId, DayOfWeek.TUESDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0)));
        verify(repository).findByEmployee_IdAndDayOfWeek(employeeId, DayOfWeek.TUESDAY);
        verify(repository, never()).save(any());
    }

    @Test
    void deletesOnlyEmployeeAndDay() {
        when(repository.findByEmployee_IdAndDayOfWeek(employeeId, DayOfWeek.MONDAY)).thenReturn(Optional.of(availability));
        service.deleteAvailability(employeeId, DayOfWeek.MONDAY);
        verify(repository).delete(availability);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void missingDeleteReturns404WithoutDeleting() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deleteAvailability(employeeId, DayOfWeek.MONDAY));
        verify(repository, never()).delete(any());
    }

    @Test
    void responseContainsOnlyAvailabilityFields() {
        assertEquals(new AvailabilityResponse(null, employeeId, DayOfWeek.MONDAY, LocalTime.of(9, 0),
                LocalTime.of(17, 0)), service.toResponse(availability));
    }

    private void assertStatus(HttpStatus expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, action).getStatusCode());
    }
}
