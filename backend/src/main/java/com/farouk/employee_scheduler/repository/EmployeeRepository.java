package com.farouk.employee_scheduler.repository;

import com.farouk.employee_scheduler.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
}
