package com.farouk.employee_scheduler.bootstrap;

import com.farouk.employee_scheduler.model.Role;
import com.farouk.employee_scheduler.model.User;
import com.farouk.employee_scheduler.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.enabled:false}")
    private boolean enabled;

    @Value("${app.bootstrap.admin.email:}")
    private String adminEmail;

    @Value("${app.bootstrap.admin.password:}")
    private String adminPassword;

    public AdminSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        if (!enabled) {
            return;
        }

        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "Admin email and password must be configured"
            );
        }

        String email = adminEmail.trim().toLowerCase();

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            System.out.println("Bootstrap account already exists.");
            return;
        }

        User admin = new User(
                email,
                passwordEncoder.encode(adminPassword),
                "System",
                "Admin",
                Role.ADMIN
        );

        userRepository.save(admin);

        System.out.println("Initial admin account created.");
    }
}