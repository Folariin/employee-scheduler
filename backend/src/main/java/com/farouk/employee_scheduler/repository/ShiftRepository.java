package com.farouk.employee_scheduler.repository;

import com.farouk.employee_scheduler.model.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {
    List<Shift> findByEmployeeId(UUID id);
}
