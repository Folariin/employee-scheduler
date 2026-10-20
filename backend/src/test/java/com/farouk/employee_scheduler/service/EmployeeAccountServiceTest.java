package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.model.Role;
import com.farouk.employee_scheduler.model.User;
import com.farouk.employee_scheduler.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeAccountServiceTest {
    @Mock UserRepository repository;
    @Mock PasswordEncoder encoder;
    @InjectMocks UserService service;

    @Test
    void createsEmployeeAccountWithNormalizedEmailAndEncodedPassword() {
        when(encoder.encode("temporary-password")).thenReturn("encoded-password");
        var saved = new User();
        when(repository.save(any(User.class))).thenReturn(saved);
        assertSame(saved, service.createEmployeeAccount(" Ada ", " Lovelace ",
                " ADA@Example.com ", "temporary-password"));
        var captured = ArgumentCaptor.forClass(User.class);
        verify(repository).findByEmailIgnoreCase("ada@example.com");
        verify(repository).save(captured.capture());
        var account = captured.getValue();
        assertAll(
                () -> assertEquals("ada@example.com", account.getEmail()),
                () -> assertEquals("Ada", account.getFirstName()),
                () -> assertEquals("Lovelace", account.getLastName()),
                () -> assertEquals("encoded-password", account.getPasswordHash()),
                () -> assertEquals(Role.EMPLOYEE, account.getRole()));
    }

    @Test
    void duplicateEmailIsCheckedIgnoringCaseBeforeHashingOrSaving() {
        when(repository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(new User()));
        var exception = assertThrows(ResponseStatusException.class, () -> service.createEmployeeAccount(
                "Ada", "Lovelace", " ADA@Example.com ", "temporary-password"));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Email is already registered", exception.getReason());
        verifyNoInteractions(encoder);
        verify(repository, never()).save(any());
    }

    @Test
    void deletesAssociatedUserById() {
        var id = UUID.randomUUID();
        service.deleteById(id);
        verify(repository).deleteById(id);
    }
}
