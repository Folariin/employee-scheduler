package com.farouk.employee_scheduler.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record GenerationRequest(@NotEmpty @Size(max=100) List<@NotNull @Valid Requirement> requirements,
                                @Min(1) @Max(168) int maxWeeklyHours, boolean balanceHoursFairly) {
    public record Requirement(@NotNull LocalDate date, @NotNull LocalTime startTime,
                              @NotNull LocalTime endTime, @Min(1) @Max(50) int requiredEmployees) {}
}
