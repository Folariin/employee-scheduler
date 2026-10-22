package com.farouk.employee_scheduler.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateWeeklyScheduleRequest(@NotNull LocalDate weekStartDate) {}
