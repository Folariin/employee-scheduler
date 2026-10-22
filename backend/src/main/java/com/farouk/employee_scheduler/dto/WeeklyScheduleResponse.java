package com.farouk.employee_scheduler.dto;

import com.farouk.employee_scheduler.model.ScheduleStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WeeklyScheduleResponse(UUID id, LocalDate weekStartDate, ScheduleStatus status, Instant createdAt) {}
