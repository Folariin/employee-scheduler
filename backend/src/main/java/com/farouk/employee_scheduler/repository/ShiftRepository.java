package com.farouk.employee_scheduler.repository;

import com.farouk.employee_scheduler.model.Shift;
import com.farouk.employee_scheduler.model.ScheduleStatus;
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
    @Query("select s.weeklySchedule.id from Shift s where s.id = :id")
    Optional<UUID> findScheduleIdById(@Param("id") UUID id);

    @Query("""
            select s from Shift s join fetch s.weeklySchedule
            where s.employee.id = :employeeId and s.weeklySchedule.status = :status
              and s.shiftDate >= :startDate and s.shiftDate <= :endDate
            order by s.shiftDate, s.startTime, s.id
            """)
    List<Shift> findVisibleShifts(@Param("employeeId") UUID employeeId,
            @Param("status") ScheduleStatus status,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

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
