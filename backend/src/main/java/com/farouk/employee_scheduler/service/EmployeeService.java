package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.model.Employee;
import com.farouk.employee_scheduler.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import com.farouk.employee_scheduler.dto.CreateEmployeeRequest;
import com.farouk.employee_scheduler.model.User;
import org.springframework.transaction.annotation.Transactional;
import com.farouk.employee_scheduler.dto.EmployeeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import com.farouk.employee_scheduler.dto.UpdateEmployeeRequest;

@Service
public class EmployeeService {
    private final EmployeeRepository employeeRepository;
    private final UserService userService;


    public EmployeeService(EmployeeRepository employeeRepository, UserService userService) {
        this.employeeRepository = employeeRepository;
        this.userService = userService;
    }

    public Employee getEmployeeById(UUID id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found"
                ));
    }

    public List<Employee> findAll() {
        return employeeRepository.findAll();
    }

    @Transactional
    public Employee createEmployee(CreateEmployeeRequest request) {

        String employeeNumber = request.employeeNumber().trim();

        if (employeeRepository.existsByEmployeeNumber(employeeNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Employee number already exists"
            );
        }

        // Step 1: Create the employee's login account
        User user = userService.createEmployeeAccount(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.temporaryPassword()
        );

        // Step 2: Create the Employee entity
        Employee employee = new Employee();

        // Step 3: Associate the employee with the user account
        employee.setUser(user);

        // Step 4: Set the employee details
        employee.setEmployeeNumber(employeeNumber);
        employee.setPhoneNumber(request.phoneNumber());
        employee.setHireDate(request.hireDate());

        // Step 5: Save the employee record
        return employeeRepository.save(employee);
    }

    @Transactional
    public Employee updateEmployee(UUID id, UpdateEmployeeRequest request) {

        Employee employee = getEmployeeById(id);

        User user = employee.getUser();

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());

        employee.setPhoneNumber(request.phoneNumber());
        employee.setHireDate(request.hireDate());

        return employeeRepository.save(employee);
    }

    public EmployeeResponse toResponse(Employee employee) {
        User user = employee.getUser();

        return new EmployeeResponse(
                employee.getId(),
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                employee.getEmployeeNumber(),
                employee.getPhoneNumber(),
                employee.getHireDate()
        );
    }

    @Transactional
    public void deleteEmployee(UUID id) {

        Employee employee = getEmployeeById(id);

        User user = employee.getUser();

        employeeRepository.delete(employee);
        employeeRepository.flush();

        userService.deleteById(user.getId());
    }

    public Employee save(Employee employee) {
        return employeeRepository.save(employee);
    }


}
