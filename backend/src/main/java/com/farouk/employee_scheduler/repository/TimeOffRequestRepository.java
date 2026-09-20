package com.farouk.employee_scheduler.repository;

import com.farouk.employee_scheduler.model.TimeOffRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimeOffRequestRepository extends JpaRepository<TimeOffRequest, UUID> {
    List<TimeOffRequest> findByEmployeeId(UUID id);
}
