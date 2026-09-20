package com.farouk.employee_scheduler.service;


import com.farouk.employee_scheduler.model.Availability;
import com.farouk.employee_scheduler.repository.AvailabilityRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AvailabilityService {
    private final AvailabilityRepository availabilityRepository;

    public AvailabilityService(AvailabilityRepository availabilityRepository) {
        this.availabilityRepository = availabilityRepository;
    }

    public Availability save(Availability availability) {
        return availabilityRepository.save(availability);
    }

    public List<Availability> findAll() {
        return availabilityRepository.findAll();
    }

    public Availability findById(UUID id) {
        return availabilityRepository.findById(id).orElse(null);
    }

    public List<Availability> findByEmployeeId(UUID id) {
        return availabilityRepository.findByEmployeeId(id);
    }
}
