package com.farouk.employee_scheduler.controller;

import com.farouk.employee_scheduler.dto.CreateEmployeeRequest;
import com.farouk.employee_scheduler.dto.EmployeeResponse;
import com.farouk.employee_scheduler.model.Employee;
import com.farouk.employee_scheduler.service.EmployeeService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.farouk.employee_scheduler.dto.UpdateEmployeeRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request
    ) {

        Employee employee = employeeService.createEmployee(request);

        EmployeeResponse response = employeeService.toResponse(employee);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public List<EmployeeResponse> getAllEmployees() {
        return employeeService.findAll()
                .stream()
                .map(employeeService::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public EmployeeResponse getEmployeeById(@PathVariable UUID id) {
        Employee employee = employeeService.getEmployeeById(id);

        return employeeService.toResponse(employee);
    }

    @PutMapping("/{id}")
    public EmployeeResponse updateEmployee(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeRequest request
    ) {
        Employee updatedEmployee = employeeService.updateEmployee(id, request);

        return employeeService.toResponse(updatedEmployee);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable UUID id) {

        employeeService.deleteEmployee(id);

        return ResponseEntity.noContent().build();
    }
}