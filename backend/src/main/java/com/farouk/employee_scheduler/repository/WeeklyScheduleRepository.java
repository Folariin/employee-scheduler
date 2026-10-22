package com.farouk.employee_scheduler.repository;

import com.farouk.employee_scheduler.model.WeeklySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WeeklyScheduleRepository extends JpaRepository<WeeklySchedule, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WeeklySchedule s where s.id = :id")
    Optional<WeeklySchedule> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByWeekStartDate(LocalDate weekStartDate);
    List<WeeklySchedule> findAllByOrderByWeekStartDateDesc();
}
