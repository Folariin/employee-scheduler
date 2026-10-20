package com.farouk.employee_scheduler.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employees")
public class Employee {
    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(nullable = false, unique = true)
    private String employeeNumber;
    private String phoneNumber;
    private LocalDate hireDate;

    public Employee(LocalDate hireDate, String phoneNumber, String employeeNumber, User user, UUID id) {
        this.hireDate = hireDate;
        this.phoneNumber = phoneNumber;
        this.employeeNumber = employeeNumber;
        this.user = user;
        this.id = id;
    }

    public Employee() {
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }
}
