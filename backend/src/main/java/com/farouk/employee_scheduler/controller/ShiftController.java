package com.farouk.employee_scheduler.controller;

import com.farouk.employee_scheduler.dto.ShiftRequest;
import com.farouk.employee_scheduler.dto.ShiftResponse;
import com.farouk.employee_scheduler.service.ShiftService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class ShiftController {
    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) { this.shiftService = shiftService; }

    @PostMapping("/api/admin/schedules/{scheduleId}/shifts")
    public ResponseEntity<ShiftResponse> create(@PathVariable UUID scheduleId, @Valid @RequestBody ShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shiftService.create(scheduleId, request));
    }

    @PutMapping("/api/admin/shifts/{id}")
    public ShiftResponse update(@PathVariable UUID id, @Valid @RequestBody ShiftRequest request) {
        return shiftService.update(id, request);
    }

    @DeleteMapping("/api/admin/shifts/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        shiftService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
