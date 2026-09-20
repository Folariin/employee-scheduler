package com.farouk.employee_scheduler.model;

import jakarta.persistence.*;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "shift")
public class Shift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    private String Day;
    private LocalTime StartTime;
    private LocalTime EndTime;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee Employee;

    public Shift(UUID id, String day, LocalTime startTime, LocalTime endTime, Employee employee) {
        this.id = id;
        Day = day;
        StartTime = startTime;
        EndTime = endTime;
        Employee = employee;
    }

    public Shift() {
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setDay(String day) {
        Day = day;
    }

    public void setStartTime(LocalTime startTime) {
        StartTime = startTime;
    }

    public void setEndTime(LocalTime endTime) {
        EndTime = endTime;
    }

    public void setEmployee(Employee employee) {
        Employee = employee;
    }

    public UUID getId() {
        return id;
    }

    public String getDay() {
        return Day;
    }

    public LocalTime getStartTime() {
        return StartTime;
    }

    public LocalTime getEndTime() {
        return EndTime;
    }

    public Employee getEmployee() {
        return Employee;
    }
}
