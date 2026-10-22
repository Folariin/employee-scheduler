package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.*;
import com.farouk.employee_scheduler.model.*;
import com.farouk.employee_scheduler.repository.ShiftRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class EmployeeScheduleService {
    private final ShiftRepository shifts;
    private final EmployeeService employees;

    public EmployeeScheduleService(ShiftRepository shifts, EmployeeService employees) {
        this.shifts = shifts;
        this.employees = employees;
    }

    private List<Shift> visible(String userId, LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.of(1, 1, 1) : startDate;
        LocalDate end = endDate == null ? LocalDate.of(9999, 12, 31) : endDate;
        if (start.getYear() < 1 || end.getYear() > 9999 || start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use dates in years 1 through 9999 with startDate on or before endDate");
        }
        UUID employeeId = employees.findByUserId(userId).getId();
        return shifts.findVisibleShifts(employeeId, ScheduleStatus.PUBLISHED, start, end);
    }

    public List<ShiftResponse> getMyShifts(String userId, LocalDate startDate, LocalDate endDate) {
        return visible(userId, startDate, endDate).stream().map(ShiftResponse::from).toList();
    }

    public List<WeeklyScheduleDetailResponse> getMySchedules(String userId, LocalDate startDate, LocalDate endDate) {
        Map<WeeklySchedule, List<ShiftResponse>> grouped = new LinkedHashMap<>();
        for (Shift shift : visible(userId, startDate, endDate)) {
            grouped.computeIfAbsent(shift.getWeeklySchedule(), ignored -> new ArrayList<>()).add(ShiftResponse.from(shift));
        }
        return grouped.entrySet().stream().map(entry -> {
            WeeklySchedule schedule = entry.getKey();
            return new WeeklyScheduleDetailResponse(schedule.getId(), schedule.getWeekStartDate(), schedule.getStatus(),
                    schedule.getCreatedAt(), List.copyOf(entry.getValue()));
        }).toList();
    }
}
