package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.model.TimeOffRequest;
import com.farouk.employee_scheduler.repository.TimeOffRequestRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TimeOffRequestService {
    private final TimeOffRequestRepository timeOffRequestRepository;

    public TimeOffRequestService(TimeOffRequestRepository timeOffRequestRepository) {
        this.timeOffRequestRepository = timeOffRequestRepository;
    }

    public List<TimeOffRequest> findAll() {
        return timeOffRequestRepository.findAll();
    }

    public List<TimeOffRequest> findByEmployeeId(UUID id) {
        return timeOffRequestRepository.findByEmployeeId(id);
    }

    public void save(TimeOffRequest timeOffRequest) {
        timeOffRequestRepository.save(timeOffRequest);
    }
}
