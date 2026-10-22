package com.farouk.employee_scheduler.security;

import jakarta.servlet.FilterChain;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {

            String token = header.substring(7);

            try {
                if (SecurityContextHolder.getContext().getAuthentication() == null
                        && jwtService.isValid(token)) {

                    String userId = jwtService.extractUserId(token);
                    String role = jwtService.extractRole(token);

                    if (userId != null && !userId.isBlank() && role != null && !role.isBlank()) {
                        var authority = new SimpleGrantedAuthority("ROLE_" + role);
                        var authentication = new UsernamePasswordAuthenticationToken(
                                userId, null, List.of(authority)
                        );

                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request)
                        );

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (JwtException | IllegalArgumentException exception) {
                // A token can expire between validation and claim extraction.
                // Leave the request anonymous so protected routes return 401.
            }
        }

        filterChain.doFilter(request, response);
    }
}
