package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.model.Availability;
import com.farouk.employee_scheduler.model.Shift;
import com.farouk.employee_scheduler.repository.ShiftRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ShiftService {
    private final ShiftRepository shiftRepository;
    public ShiftService(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    public Shift findById(UUID id) {
        return shiftRepository.findById(id).orElse(null);
    }

    public void save(Shift shift) {
        shiftRepository.save(shift);
    }

    public List<Shift> findByEmployeeId(UUID id) {
        return shiftRepository.findByEmployeeId(id);
    }
}
