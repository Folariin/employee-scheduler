package com.farouk.employee_scheduler.repository;

import com.farouk.employee_scheduler.model.Availability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {
    List<Availability> findByEmployeeId(UUID id);
    boolean existsByEmployee_IdAndDayOfWeek(
            UUID employeeId,
            DayOfWeek dayOfWeek
    );

    Optional<Availability> findByEmployee_IdAndDayOfWeek(
            UUID employeeId,
            DayOfWeek dayOfWeek
    );
}
