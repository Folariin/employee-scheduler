package com.farouk.employee_scheduler.controller;
import com.farouk.employee_scheduler.dto.AvailabilityResponse;
import com.farouk.employee_scheduler.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/admin/employees")
public class AdminAvailabilityController {
    private final EmployeeService employees;private final AvailabilityService availability;
    public AdminAvailabilityController(EmployeeService employees,AvailabilityService availability){this.employees=employees;this.availability=availability;}
    @GetMapping("/{id}/availability")
    public List<AvailabilityResponse> read(@PathVariable UUID id) {
        employees.getEmployeeById(id);
        return availability.findByEmployeeId(id).stream()
            .sorted(Comparator.comparing(a->a.getDayOfWeek()))
            .map(availability::toResponse).toList();
    }
}
