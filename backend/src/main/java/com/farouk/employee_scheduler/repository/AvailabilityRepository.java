package com.farouk.employee_scheduler.repository;

import com.farouk.employee_scheduler.model.Availability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {
    List<Availability> findByEmployeeId(UUID id);
}
