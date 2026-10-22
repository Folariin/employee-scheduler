package com.farouk.employee_scheduler.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {
    @Mock JwtService jwt;
    @Mock FilterChain chain;

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void expirationDuringClaimExtractionLeavesRequestAnonymous() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        var response = new MockHttpServletResponse();
        when(jwt.isValid("token")).thenReturn(true);
        when(jwt.extractUserId("token")).thenThrow(new JwtException("expired during extraction"));
        new JwtAuthFilter(jwt).doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void existingAuthenticationIsPreservedWithoutParsingBearerToken() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken("existing", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        new JwtAuthFilter(jwt).doFilter(request, new MockHttpServletResponse(), chain);
        assertSame(authentication, SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwt);
    }

    @Test
    void downstreamApplicationExceptionsAreNotSwallowed() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var failure = new IllegalArgumentException("application error");
        doThrow(failure).when(chain).doFilter(request, response);
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> new JwtAuthFilter(jwt).doFilter(request, response, chain)));
        verify(chain).doFilter(request, response);
    }
}
