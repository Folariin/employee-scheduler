package com.farouk.employee_scheduler.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import com.farouk.employee_scheduler.model.User;
import com.farouk.employee_scheduler.repository.UserRepository;
import org.springframework.stereotype.Service;

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

    public static class InvalidCredentialsException extends RuntimeException {}


}
