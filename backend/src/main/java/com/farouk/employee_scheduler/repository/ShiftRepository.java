package com.farouk.employee_scheduler.repository;

import com.farouk.employee_scheduler.model.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import java.util.List;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {
    List<Shift> findByEmployeeId(UUID id);

    List<Shift> findByWeeklyScheduleIdOrderByShiftDateAscStartTimeAscIdAsc(UUID scheduleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shift s where s.id = :id")
    Optional<Shift> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select count(s) > 0 from Shift s
            where s.employee.id = :employeeId and s.shiftDate = :shiftDate
              and s.startTime < :endTime and s.endTime > :startTime
              and (:excludedId is null or s.id <> :excludedId)
            """)
    boolean existsOverlapping(@Param("employeeId") UUID employeeId, @Param("shiftDate") LocalDate shiftDate,
                              @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime,
                              @Param("excludedId") UUID excludedId);
}
