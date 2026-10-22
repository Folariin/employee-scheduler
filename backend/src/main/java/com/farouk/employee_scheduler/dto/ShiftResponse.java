package com.farouk.employee_scheduler.dto;

import com.farouk.employee_scheduler.model.Shift;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ShiftResponse(UUID id, UUID scheduleId, UUID employeeId, LocalDate shiftDate,
                            LocalTime startTime, LocalTime endTime) {
    public static ShiftResponse from(Shift shift) {
        return new ShiftResponse(shift.getId(), shift.getWeeklySchedule().getId(), shift.getEmployee().getId(),
                shift.getShiftDate(), shift.getStartTime(), shift.getEndTime());
    }
}
