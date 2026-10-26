package com.farouk.employee_scheduler.controller;
import com.farouk.employee_scheduler.service.EmployeeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/employees")
public class EmployeeProfileController {
    private final EmployeeService employees;
    public EmployeeProfileController(EmployeeService employees){this.employees=employees;}
    public record Profile(String firstName,String lastName) {}
    @GetMapping("/me")
    public Profile me(@AuthenticationPrincipal String userId) {
        var employee=employees.findByUserId(userId);
        var user=employee.getUser();
        return new Profile(user.getFirstName(),user.getLastName());
    }
}
