package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.*;
import com.farouk.employee_scheduler.model.WeeklySchedule;
import com.farouk.employee_scheduler.model.ScheduleStatus;
import com.farouk.employee_scheduler.repository.ShiftRepository;
import com.farouk.employee_scheduler.repository.WeeklyScheduleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class WeeklyScheduleService {
    private final WeeklyScheduleRepository scheduleRepository;
    private final ShiftRepository shiftRepository;

    public WeeklyScheduleService(WeeklyScheduleRepository scheduleRepository, ShiftRepository shiftRepository) {
        this.scheduleRepository = scheduleRepository;
        this.shiftRepository = shiftRepository;
    }

    public WeeklyScheduleResponse create(CreateWeeklyScheduleRequest request) {
        LocalDate date = request.weekStartDate();
        if (date == null || date.getDayOfWeek() != DayOfWeek.MONDAY || date.getYear() < 1 || date.getYear() > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weekStartDate must be a Monday in years 1 through 9999");
        }
        if (scheduleRepository.existsByWeekStartDate(date)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A schedule already exists for this week");
        }
        try {
            return toResponse(scheduleRepository.saveAndFlush(new WeeklySchedule(date)));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "A schedule already exists for this week", exception);
                }
            }
            throw exception;
        }
    }

    // Every schedule mutation uses this row lock before acquiring any shift lock.
    @Transactional(propagation = Propagation.MANDATORY)
    public WeeklySchedule lockForModification(UUID id) {
        return scheduleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Weekly schedule not found"));
    }

    @Transactional
    public WeeklyScheduleResponse publish(UUID id) {
        WeeklySchedule schedule = lockForModification(id);
        if (schedule.getStatus() != ScheduleStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Schedule is already published");
        }
        schedule.publish();
        return toResponse(scheduleRepository.saveAndFlush(schedule));
    }

    @Transactional(readOnly = true)
    public List<WeeklyScheduleResponse> findAll() {
        return scheduleRepository.findAllByOrderByWeekStartDateDesc().stream().map(this::toResponse).toList();
    }

    public WeeklySchedule findById(UUID id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Weekly schedule not found"));
    }

    @Transactional(readOnly = true)
    public WeeklyScheduleDetailResponse getDetail(UUID id) {
        WeeklySchedule schedule = findById(id);
        List<ShiftResponse> shifts = shiftRepository.findByWeeklyScheduleIdOrderByShiftDateAscStartTimeAscIdAsc(id)
                .stream().map(ShiftResponse::from).toList();
        return new WeeklyScheduleDetailResponse(schedule.getId(), schedule.getWeekStartDate(), schedule.getStatus(),
                schedule.getCreatedAt(), shifts);
    }

    private WeeklyScheduleResponse toResponse(WeeklySchedule schedule) {
        return new WeeklyScheduleResponse(schedule.getId(), schedule.getWeekStartDate(), schedule.getStatus(), schedule.getCreatedAt());
    }
}
