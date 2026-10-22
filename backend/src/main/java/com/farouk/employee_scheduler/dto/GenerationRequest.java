package com.farouk.employee_scheduler.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record GenerationRequest(@NotEmpty @Size(max=100) List<@NotNull @Valid Requirement> requirements,
        @Min(1) @Max(168) int maxWeeklyHours, boolean balanceHoursFairly,
        @Min(1) @Max(1440) Integer minShiftMinutes, @Min(1) @Max(1440) Integer maxShiftMinutes) {
    public enum Mode { FIXED, FLEXIBLE }
    // Retain the original Java and JSON contract: omitted mode means FIXED.
    public GenerationRequest(List<Requirement> requirements,int maxWeeklyHours,boolean balanceHoursFairly) {
        this(requirements,maxWeeklyHours,balanceHoursFairly,null,null);
    }
    public int effectiveMinShiftMinutes(){return minShiftMinutes==null?120:minShiftMinutes;}
    public int effectiveMaxShiftMinutes(){return maxShiftMinutes==null?480:maxShiftMinutes;}
    public record Requirement(@NotNull LocalDate date, @NotNull LocalTime startTime,
            @NotNull LocalTime endTime, @Min(1) @Max(50) int requiredEmployees, Mode mode) {
        public Requirement { if(mode==null)mode=Mode.FIXED; }
        public Requirement(LocalDate date,LocalTime startTime,LocalTime endTime,int requiredEmployees) {
            this(date,startTime,endTime,requiredEmployees,Mode.FIXED);
        }
    }
}
