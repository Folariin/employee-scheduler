
package com.farouk.employee_scheduler.model;

import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
        name = "availability",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"employee_id", "day_of_week"}
        )
)
public class Availability {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // Required by JPA
    public Availability() {
    }

    // Constructor
    public Availability(
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Employee employee
    ) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.employee = employee;
    }

    // Getters

    public UUID getId() {
        return id;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Employee getEmployee() {
        return employee;
    }

    // Setters

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }
}
