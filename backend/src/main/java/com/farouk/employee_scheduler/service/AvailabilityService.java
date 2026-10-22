package com.farouk.employee_scheduler.service;


import com.farouk.employee_scheduler.dto.AvailabilityResponse;
import com.farouk.employee_scheduler.model.Availability;
import com.farouk.employee_scheduler.repository.AvailabilityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalTime;
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

        // Validate the time range
        if (!availability.getStartTime().isBefore(availability.getEndTime())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start time must be before end time"
            );
        }

        // Check whether availability already exists for this day
        boolean alreadyExists =
                availabilityRepository.existsByEmployee_IdAndDayOfWeek(
                        availability.getEmployee().getId(),
                        availability.getDayOfWeek()
                );

        if (alreadyExists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Availability already exists for this day"
            );
        }

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

    public AvailabilityResponse toResponse(Availability availability) {
        return new AvailabilityResponse(
                availability.getId(),
                availability.getEmployee().getId(),
                availability.getDayOfWeek(),
                availability.getStartTime(),
                availability.getEndTime()
        );
    }

    public Availability updateAvailability(
            UUID employeeId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime
    ) {
        if (!startTime.isBefore(endTime)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start time must be before end time"
            );
        }

        Availability availability = availabilityRepository
                .findByEmployee_IdAndDayOfWeek(employeeId, dayOfWeek)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No availability found for " + dayOfWeek
                ));

        availability.setStartTime(startTime);
        availability.setEndTime(endTime);

        return availabilityRepository.save(availability);
    }

    public void deleteAvailability(UUID employeeId, DayOfWeek dayOfWeek) {

        Availability availability = availabilityRepository
                .findByEmployee_IdAndDayOfWeek(employeeId, dayOfWeek)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No availability found for " + dayOfWeek
                ));

        availabilityRepository.delete(availability);
    }
}
