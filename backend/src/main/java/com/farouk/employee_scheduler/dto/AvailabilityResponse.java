
package com.farouk.employee_scheduler.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityResponse(

        UUID id,

        UUID employeeId,

        DayOfWeek dayOfWeek,

        LocalTime startTime,

        LocalTime endTime

) {}
