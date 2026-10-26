package com.farouk.employee_scheduler.dto;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record ApplyGenerationRequest(@NotNull UUID previewToken,
        @NotEmpty @Size(max=1000) List<@NotBlank @Size(max=80) String> suggestionIds) {}
