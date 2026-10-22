package com.farouk.employee_scheduler.dto;

import com.farouk.employee_scheduler.model.ScheduleStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WeeklyScheduleDetailResponse(UUID id, LocalDate weekStartDate, ScheduleStatus status,
                                         Instant createdAt, List<ShiftResponse> shifts) {}
