package com.farouk.employee_scheduler.dto;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        UUID userId,
        String firstName,
        String lastName,
        String email,
        String employeeNumber,
        String phoneNumber,
        LocalDate hireDate
) {}