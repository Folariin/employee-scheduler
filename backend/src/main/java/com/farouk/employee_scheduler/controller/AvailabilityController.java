package com.farouk.employee_scheduler.controller;

import com.farouk.employee_scheduler.dto.AvailabilityResponse;
import com.farouk.employee_scheduler.dto.CreateAvailabilityRequest;
import com.farouk.employee_scheduler.model.Availability;
import com.farouk.employee_scheduler.model.Employee;
import com.farouk.employee_scheduler.service.AvailabilityService;
import com.farouk.employee_scheduler.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.util.List;

@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {
    private final AvailabilityService availabilityService;
    private final EmployeeService employeeService;
    public AvailabilityController(AvailabilityService availabilityService, EmployeeService employeeService) {
        this.availabilityService = availabilityService;
        this.employeeService = employeeService;
    }

    @PostMapping("/me")
    public ResponseEntity<AvailabilityResponse> createAvailability(@AuthenticationPrincipal String userId, @Valid @RequestBody CreateAvailabilityRequest req) {
        Employee employee= employeeService.findByUserId(userId);
        Availability availability = new Availability(req.dayOfWeek(), req.startTime(), req.endTime(), employee);
        Availability savedAvailability = availabilityService.save(availability);
        AvailabilityResponse response= availabilityService.toResponse(savedAvailability);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<AvailabilityResponse>> getMyAvailability(
            @AuthenticationPrincipal String userId
    ) {

        Employee employee = employeeService.findByUserId(userId);

        List<AvailabilityResponse> response = availabilityService
                .findByEmployeeId(employee.getId())
                .stream()
                .map(availabilityService::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/{dayOfWeek}")
    public ResponseEntity<AvailabilityResponse> updateAvailability(
            @AuthenticationPrincipal String userId,
            @PathVariable DayOfWeek dayOfWeek,
            @Valid @RequestBody CreateAvailabilityRequest req
    ) {

        if (dayOfWeek != req.dayOfWeek()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request dayOfWeek must match the URL dayOfWeek"
            );
        }

        Employee employee = employeeService.findByUserId(userId);

        Availability updatedAvailability = availabilityService.updateAvailability(
                employee.getId(),
                dayOfWeek,
                req.startTime(),
                req.endTime()
        );

        AvailabilityResponse response =
                availabilityService.toResponse(updatedAvailability);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me/{dayOfWeek}")
    public ResponseEntity<Void> deleteAvailability(
            @AuthenticationPrincipal String userId,
            @PathVariable DayOfWeek dayOfWeek
    ) {

        Employee employee = employeeService.findByUserId(userId);

        availabilityService.deleteAvailability(
                employee.getId(),
                dayOfWeek
        );

        return ResponseEntity.noContent().build();
    }

}
