package com.farouk.employee_scheduler.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ShiftRequest(@NotNull UUID employeeId, @NotNull LocalDate shiftDate,
                           @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}
