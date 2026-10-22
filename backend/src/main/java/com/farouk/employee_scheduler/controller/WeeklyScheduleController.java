package com.farouk.employee_scheduler.controller;

import com.farouk.employee_scheduler.dto.*;
import com.farouk.employee_scheduler.service.WeeklyScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/schedules")
public class WeeklyScheduleController {
    private final WeeklyScheduleService scheduleService;

    public WeeklyScheduleController(WeeklyScheduleService scheduleService) { this.scheduleService = scheduleService; }

    @PostMapping
    public ResponseEntity<WeeklyScheduleResponse> create(@Valid @RequestBody CreateWeeklyScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.create(request));
    }

    @PostMapping("/{id}/publish")
    public WeeklyScheduleResponse publish(@PathVariable UUID id) { return scheduleService.publish(id); }

    @GetMapping
    public List<WeeklyScheduleResponse> findAll() { return scheduleService.findAll(); }

    @GetMapping("/{id}")
    public WeeklyScheduleDetailResponse getDetail(@PathVariable UUID id) { return scheduleService.getDetail(id); }
}
