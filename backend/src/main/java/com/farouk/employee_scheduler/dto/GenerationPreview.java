package com.farouk.employee_scheduler.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record GenerationPreview(UUID previewToken, UUID scheduleId, Instant expiresAt,
        List<Assignment> assignments, List<Coverage> coverage, List<Workload> workloads) {
    public record Assignment(String suggestionId, int requirementIndex, UUID employeeId, String displayName,
                             LocalDate shiftDate, LocalTime startTime, LocalTime endTime) {}
    public record Coverage(int requirementIndex, LocalDate date, LocalTime startTime, LocalTime endTime,
                           int requiredEmployees, int existingEmployees, int suggestedEmployees,
                           int shortage, String explanation, GenerationRequest.Mode mode) {}
    public record Workload(UUID employeeId, String displayName, BigDecimal existingWeeklyHours,
                           BigDecimal proposedWeeklyHours, BigDecimal resultingWeeklyHours) {}
}
