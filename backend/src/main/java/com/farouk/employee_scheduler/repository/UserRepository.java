package com.farouk.employee_scheduler.repository;

import com.farouk.employee_scheduler.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

}
