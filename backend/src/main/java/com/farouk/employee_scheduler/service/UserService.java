package com.farouk.employee_scheduler.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import com.farouk.employee_scheduler.model.User;
import com.farouk.employee_scheduler.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.farouk.employee_scheduler.model.Role;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;

    public UserService(UserRepository userRepository, PasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.encoder = encoder;
    }

    public void save(User user) {
        userRepository.save(user);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(UUID id) {
        return userRepository.findById(id).orElse(null);
    }

    public void deleteById(UUID id) {
        userRepository.deleteById(id);
    }

    public Optional<User> findByEmail(String email){
        return userRepository.findByEmailIgnoreCase(email.trim());
    }

    public boolean passwordMatches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }

    public User createEmployeeAccount(
            String firstName,
            String lastName,
            String email,
            String temporaryPassword
    ) {
        String normalizedEmail = email.trim().toLowerCase();

        // Check whether the email already exists
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        // Hash the temporary password
        String hashedPassword = encoder.encode(temporaryPassword);

        // Create the user with the EMPLOYEE role
        User user = new User(
                normalizedEmail,
                hashedPassword,
                firstName.trim(),
                lastName.trim(),
                Role.EMPLOYEE
        );

        // Save the user and return the saved entity
        return userRepository.save(user);
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class InvalidCredentialsException
            extends RuntimeException {

        public InvalidCredentialsException() {
            super("Invalid email or password");
        }
    }


}
