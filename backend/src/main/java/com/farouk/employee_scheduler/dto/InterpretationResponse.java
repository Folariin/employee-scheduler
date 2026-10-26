package com.farouk.employee_scheduler.dto;
import java.util.List;
public record InterpretationResponse(List<GenerationRequest.Requirement> requirements,
        Integer maxWeeklyHours, Boolean balanceHoursFairly, List<String> clarificationQuestions,
        List<String> unsupportedConstraints, String summary) {}
