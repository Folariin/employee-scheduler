package com.farouk.employee_scheduler.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class JwtService {

    private static final List<String> PLACEHOLDER_MARKERS = List.of(
            "CHANGE_ME",
            "YOUR_",
            "PLACEHOLDER",
            "EXAMPLE"
    );

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs,
            @Value("${app.environment:local}") String appEnvironment
    ) {

        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException(
                    "jwt.secret must be at least 32 characters"
            );
        }

        if (isProduction(appEnvironment) && isPlaceholderSecret(secret)) {
            throw new IllegalArgumentException(
                    "JWT_SECRET must be set to a real production secret"
            );
        }

        this.key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        this.expirationMs = expirationMs;
    }

    public String generate(String userId, Map<String, Object> claims) {
        return Jwts.builder()
                .setSubject(userId)
                .addClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(
                        new Date(System.currentTimeMillis() + expirationMs)
                )
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUserId(String token) {
        return parse(token)
                .getBody()
                .getSubject();
    }

    public String extractRole(String token) {
        return parse(token)
                .getBody()
                .get("role", String.class);
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
    }

    private boolean isProduction(String appEnvironment) {
        return "production".equalsIgnoreCase(appEnvironment)
                || "prod".equalsIgnoreCase(appEnvironment);
    }

    private boolean isPlaceholderSecret(String secret) {
        String normalized = secret.toUpperCase();

        return PLACEHOLDER_MARKERS.stream()
                .anyMatch(normalized::contains);
    }
}