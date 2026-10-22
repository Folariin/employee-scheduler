package com.farouk.employee_scheduler.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "weekly_schedule",
        uniqueConstraints = @UniqueConstraint(name = "uk_weekly_schedule_start", columnNames = "week_start_date"),
        check = @CheckConstraint(name = "ck_weekly_schedule_monday", constraint = "extract(isodow from week_start_date) = 1"))
public class WeeklySchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "week_start_date", nullable = false, updatable = false)
    private LocalDate weekStartDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleStatus status = ScheduleStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WeeklySchedule() {}

    public WeeklySchedule(LocalDate weekStartDate) { this.weekStartDate = weekStartDate; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public ScheduleStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
