package com.farouk.employee_scheduler.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "time_off_request")
public class TimeOffRequest {
    @Id
    @GeneratedValue
    private UUID id;

    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
    @Enumerated(EnumType.STRING)
    private TimeOffStatus status = TimeOffStatus.PENDING;

    public TimeOffRequest(UUID id, LocalDate startDate, LocalDate endDate, String reason, Employee employee, TimeOffStatus status) {
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.employee = employee;
        this.status = status;
    }

    public TimeOffRequest() {
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public void setStatus(TimeOffStatus status) {
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getReason() {
        return reason;
    }

    public Employee getEmployee() {
        return employee;
    }

    public TimeOffStatus getStatus() {
        return status;
    }
}
