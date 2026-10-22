package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.ShiftRequest;
import com.farouk.employee_scheduler.dto.ShiftResponse;
import com.farouk.employee_scheduler.model.ScheduleStatus;
import com.farouk.employee_scheduler.model.WeeklySchedule;
import com.farouk.employee_scheduler.model.Shift;
import com.farouk.employee_scheduler.repository.EmployeeRepository;
import com.farouk.employee_scheduler.repository.AvailabilityRepository;
import com.farouk.employee_scheduler.repository.ShiftRepository;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;

import java.util.List;
import java.util.UUID;

@Service
public class ShiftService {
    private final ShiftRepository shiftRepository;
    private final EmployeeRepository employeeRepository;
    private final AvailabilityRepository availabilityRepository;
    private final WeeklyScheduleService scheduleService;

    public ShiftService(ShiftRepository shiftRepository, EmployeeRepository employeeRepository,
                        AvailabilityRepository availabilityRepository, WeeklyScheduleService scheduleService) {
        this.shiftRepository = shiftRepository;
        this.employeeRepository = employeeRepository;
        this.availabilityRepository = availabilityRepository;
        this.scheduleService = scheduleService;
    }

    public Shift findById(UUID id) {
        return shiftRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
    }

    @Transactional
    public ShiftResponse create(UUID scheduleId, ShiftRequest request) {
        WeeklySchedule schedule = scheduleService.findById(scheduleId);
        Shift shift = new Shift();
        shift.setWeeklySchedule(schedule);
        applyAssignment(shift, request);
        return persist(shift);
    }

    @Transactional
    public ShiftResponse update(UUID id, ShiftRequest request) {
        Shift shift = shiftRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
        applyAssignment(shift, request);
        return persist(shift);
    }

    @Transactional
    public void delete(UUID id) {
        Shift shift = shiftRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
        requireDraft(shift.getWeeklySchedule());
        shiftRepository.delete(shift);
        shiftRepository.flush();
    }

    private void applyAssignment(Shift shift, ShiftRequest request) {
        WeeklySchedule schedule = shift.getWeeklySchedule();
        requireDraft(schedule);
        if (request.shiftDate() == null || request.startTime() == null || request.endTime() == null
                || !request.startTime().isBefore(request.endTime())
                || request.startTime().getNano() != 0 || request.endTime().getNano() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use whole-second times with startTime before endTime; overnight shifts are unsupported");
        }
        long offset = ChronoUnit.DAYS.between(schedule.getWeekStartDate(), request.shiftDate());
        if (offset < 0 || offset > 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shiftDate must belong to the selected schedule week");
        }
        if (request.employeeId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee ID is required");
        }
        var employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        var availability = availabilityRepository.findByEmployee_IdAndDayOfWeek(employee.getId(), request.shiftDate().getDayOfWeek())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Employee has no availability for this weekday"));
        if (request.startTime().isBefore(availability.getStartTime()) || request.endTime().isAfter(availability.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Shift must be fully covered by employee availability");
        }
        if (shiftRepository.existsOverlapping(employee.getId(), request.shiftDate(), request.startTime(), request.endTime(), shift.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee already has an overlapping shift");
        }
        shift.setEmployee(employee);
        shift.setShiftDate(request.shiftDate());
        shift.setStartTime(request.startTime());
        shift.setEndTime(request.endTime());
    }

    private void requireDraft(WeeklySchedule schedule) {
        if (schedule.getStatus() != ScheduleStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT schedules can be modified");
        }
    }

    private ShiftResponse persist(Shift shift) {
        try {
            // The PostgreSQL exclusion constraint is the final arbiter for races.
            return ShiftResponse.from(shiftRepository.saveAndFlush(shift));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "23P01".equals(sql.getSQLState())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee already has an overlapping shift", exception);
                }
            }
            throw exception;
        } catch (ConcurrencyFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent shift change; retry the request", exception);
        }
    }

    public List<Shift> findByEmployeeId(UUID id) {
        return shiftRepository.findByEmployeeId(id);
    }
}
