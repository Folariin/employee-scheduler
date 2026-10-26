package com.farouk.employee_scheduler.dto;
import jakarta.validation.constraints.*;
public record InterpretationRequest(@NotBlank @Size(max=4000) String instructions) {}
