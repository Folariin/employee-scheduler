package com.farouk.employee_scheduler.service;


import com.farouk.employee_scheduler.dto.AvailabilityResponse;
import com.farouk.employee_scheduler.model.Availability;
import com.farouk.employee_scheduler.repository.AvailabilityRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Service
public class AvailabilityService {
    private final AvailabilityRepository availabilityRepository;

    public AvailabilityService(AvailabilityRepository availabilityRepository) {
        this.availabilityRepository = availabilityRepository;
    }

    public Availability save(Availability availability) {

        // Validate the time range
        if (availability.getDayOfWeek() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Day of week is required");
        }
        validateTimeRange(availability.getStartTime(), availability.getEndTime());

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

        try {
            // Flush inside the repository transaction so concurrent duplicate inserts
            // are translated here instead of surfacing as an unhandled commit error.
            return availabilityRepository.saveAndFlush(availability);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sqlException
                        && "23505".equals(sqlException.getSQLState())) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Availability already exists for this day",
                            exception
                    );
                }
            }
            throw exception;
        }
    }

    public List<Availability> findAll() {
        return availabilityRepository.findAll();
    }

    public Availability findById(UUID id) {
        return availabilityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Availability not found"));
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

    @Transactional
    public Availability updateAvailability(
            UUID employeeId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime
    ) {
        validateTimeRange(startTime, endTime);

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

    @Transactional
    public void deleteAvailability(UUID employeeId, DayOfWeek dayOfWeek) {

        Availability availability = availabilityRepository
                .findByEmployee_IdAndDayOfWeek(employeeId, dayOfWeek)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No availability found for " + dayOfWeek
                ));

        availabilityRepository.delete(availability);
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start time must be before end time"
            );
        }
    }
}
