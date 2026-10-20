package com.farouk.employee_scheduler.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateEmployeeRequest(

        @NotBlank String firstName,

        @NotBlank String lastName,

        @Email @NotBlank String email,

        @NotBlank String employeeNumber,

        String phoneNumber,

        @NotNull LocalDate hireDate,

        @NotBlank String temporaryPassword

) {}