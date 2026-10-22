package com.farouk.employee_scheduler.repository;

import com.farouk.employee_scheduler.model.WeeklySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WeeklyScheduleRepository extends JpaRepository<WeeklySchedule, UUID> {
    boolean existsByWeekStartDate(LocalDate weekStartDate);
    List<WeeklySchedule> findAllByOrderByWeekStartDateDesc();
}
