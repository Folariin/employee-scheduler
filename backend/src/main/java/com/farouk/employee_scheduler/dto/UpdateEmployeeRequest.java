package com.farouk.employee_scheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record UpdateEmployeeRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String phoneNumber,
        @NotNull LocalDate hireDate
) {
}