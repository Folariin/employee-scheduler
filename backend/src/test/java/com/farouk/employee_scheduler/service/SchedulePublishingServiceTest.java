package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.model.*;
import com.farouk.employee_scheduler.dto.ShiftRequest;
import com.farouk.employee_scheduler.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchedulePublishingServiceTest {
    WeeklyScheduleRepository repository = mock(WeeklyScheduleRepository.class);
    ShiftRepository shifts = mock(ShiftRepository.class);
    WeeklyScheduleService service = new WeeklyScheduleService(repository, shifts);
    UUID id = UUID.randomUUID();
    WeeklySchedule schedule = new WeeklySchedule(LocalDate.of(2026, 10, 26));

    @Test void publishesUsingExclusiveLockWithoutChangingAssignments() {
        schedule.setId(id);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(schedule));
        when(repository.saveAndFlush(schedule)).thenReturn(schedule);
        var result = service.publish(id);
        assertEquals(ScheduleStatus.PUBLISHED, result.status());
        assertEquals(id, result.id());
        assertEquals(schedule.getCreatedAt(), result.createdAt());
        var order = inOrder(repository);
        order.verify(repository).findByIdForUpdate(id);
        order.verify(repository).saveAndFlush(schedule);
        verifyNoInteractions(shifts);
    }
    @Test void missingScheduleIs404() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.publish(id)).getStatusCode().value());
        verify(repository, never()).saveAndFlush(any());
    }
    @Test void repeatedPublicationIs409() {
        schedule.publish();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(schedule));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.publish(id)).getStatusCode().value());
        verify(repository, never()).saveAndFlush(any());
    }
    @ParameterizedTest @ValueSource(strings = {"create", "update", "delete"})
    void publishedScheduleRejectsEveryMutationBeforeAssignmentValidation(String operation) {
        schedule.publish();
        WeeklyScheduleService schedules = mock(WeeklyScheduleService.class);
        when(schedules.lockForModification(id)).thenReturn(schedule);
        UUID shiftId = UUID.randomUUID();
        if (!operation.equals("create")) when(shifts.findScheduleIdById(shiftId)).thenReturn(Optional.of(id));
        EmployeeRepository employees = mock(EmployeeRepository.class);
        AvailabilityRepository availability = mock(AvailabilityRepository.class);
        ShiftService mutations = new ShiftService(shifts, employees, availability, schedules);
        ShiftRequest request = new ShiftRequest(UUID.randomUUID(), schedule.getWeekStartDate(), LocalTime.NOON, LocalTime.of(13, 0));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> {
            switch (operation) {
                case "create" -> mutations.create(id, request);
                case "update" -> mutations.update(shiftId, request);
                default -> mutations.delete(shiftId);
            }
        }).getStatusCode().value());
        verifyNoInteractions(employees, availability);
        verify(shifts, never()).saveAndFlush(any());
        verify(shifts, never()).delete(any());
        verify(shifts, never()).findByIdForUpdate(any());
    }
}
