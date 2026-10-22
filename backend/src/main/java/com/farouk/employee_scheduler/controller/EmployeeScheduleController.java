package com.farouk.employee_scheduler.controller;

import com.farouk.employee_scheduler.dto.*;
import com.farouk.employee_scheduler.service.EmployeeScheduleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
public class EmployeeScheduleController {
    private final EmployeeScheduleService schedules;
    public EmployeeScheduleController(EmployeeScheduleService schedules) { this.schedules = schedules; }

    @GetMapping("/api/shifts/me")
    public List<ShiftResponse> shifts(Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return schedules.getMyShifts(principal.getName(), startDate, endDate);
    }

    @GetMapping("/api/schedules/me")
    public List<WeeklyScheduleDetailResponse> schedules(Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return schedules.getMySchedules(principal.getName(), startDate, endDate);
    }
}
