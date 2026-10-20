package com.farouk.employee_scheduler.controller;
import com.farouk.employee_scheduler.dto.AuthDtos;
import com.farouk.employee_scheduler.model.User;
import com.farouk.employee_scheduler.service.UserService;
import com.farouk.employee_scheduler.security.JwtService;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest req) {

        User u = userService.findByEmail(req.email())
                .orElseThrow(UserService.InvalidCredentialsException::new);

        if (!userService.passwordMatches(req.password(), u.getPasswordHash())) {
            throw new UserService.InvalidCredentialsException();
        }

        String role = u.getRole().name();

        String token = jwtService.generate(
                u.getId().toString(),
                Map.of("email", u.getEmail(), "role", role)
        );

        return ResponseEntity.ok(new AuthDtos.AuthResponse(token, role));
    }

}
