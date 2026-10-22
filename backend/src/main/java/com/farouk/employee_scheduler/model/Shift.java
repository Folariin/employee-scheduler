package com.farouk.employee_scheduler.model;

import jakarta.persistence.*;

import java.time.LocalTime;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "shift", indexes = {
        @Index(name = "ix_shift_employee_date", columnList = "employee_id,shift_date"),
        @Index(name = "ix_shift_schedule_date", columnList = "weekly_schedule_id,shift_date")
}, check = @CheckConstraint(name = "ck_shift_time_range", constraint = "start_time < end_time"))
public class Shift {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "weekly_schedule_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_shift_weekly_schedule"))
    private WeeklySchedule weeklySchedule;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(name = "start_time", nullable = false, columnDefinition = "time(0)")
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false, columnDefinition = "time(0)")
    private LocalTime endTime;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    public Shift(WeeklySchedule weeklySchedule, Employee employee, LocalDate shiftDate,
                 LocalTime startTime, LocalTime endTime) {
        this.weeklySchedule = weeklySchedule;
        this.employee = employee;
        this.shiftDate = shiftDate;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Shift() {
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setWeeklySchedule(WeeklySchedule weeklySchedule) { this.weeklySchedule = weeklySchedule; }

    public void setShiftDate(LocalDate shiftDate) { this.shiftDate = shiftDate; }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public UUID getId() {
        return id;
    }

    public WeeklySchedule getWeeklySchedule() { return weeklySchedule; }

    public LocalDate getShiftDate() { return shiftDate; }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Employee getEmployee() {
        return employee;
    }
}
