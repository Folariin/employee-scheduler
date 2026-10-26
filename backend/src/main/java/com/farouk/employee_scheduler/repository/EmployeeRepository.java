package com.farouk.employee_scheduler.repository;

import com.farouk.employee_scheduler.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    boolean existsByEmployeeNumber(String employeeNumber);
    Optional<Employee> findByUser_Id(UUID userId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select e from Employee e order by e.id")
    java.util.List<Employee> findAllLockedForScheduling();
}
