package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.CreateEmployeeRequest;
import com.farouk.employee_scheduler.dto.EmployeeResponse;
import com.farouk.employee_scheduler.dto.UpdateEmployeeRequest;
import com.farouk.employee_scheduler.model.Employee;
import com.farouk.employee_scheduler.model.Role;
import com.farouk.employee_scheduler.model.User;
import com.farouk.employee_scheduler.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {
    @Mock EmployeeRepository repository;
    @Mock UserService userService;
    @InjectMocks EmployeeService service;

    private final UUID id = UUID.randomUUID();
    private final LocalDate hireDate = LocalDate.of(2026, 1, 15);
    private User user;
    private Employee employee;
    private CreateEmployeeRequest request;

    @BeforeEach
    void setUp() {
        user = new User("ada@example.com", "hashed", "Ada", "Lovelace", Role.EMPLOYEE);
        user.setId(UUID.randomUUID());
        employee = new Employee(hireDate, "2045550100", "EMP001", user, id);
        request = new CreateEmployeeRequest("Ada", "Lovelace", "ada@example.com",
                " EMP001 ", "2045550100", hireDate, "temporary-password");
    }

    @Test
    void createsEmployeeWithTrimmedNumberAndAssociatedAccount() {
        when(userService.createEmployeeAccount("Ada", "Lovelace", "ada@example.com", "temporary-password"))
                .thenReturn(user);
        when(repository.save(any(Employee.class))).thenReturn(employee);

        assertSame(employee, service.createEmployee(request));

        var captured = ArgumentCaptor.forClass(Employee.class);
        verify(repository).existsByEmployeeNumber("EMP001");
        verify(repository).save(captured.capture());
        assertAll(
                () -> assertEquals("EMP001", captured.getValue().getEmployeeNumber()),
                () -> assertSame(user, captured.getValue().getUser()),
                () -> assertEquals(request.phoneNumber(), captured.getValue().getPhoneNumber()),
                () -> assertEquals(hireDate, captured.getValue().getHireDate()));
    }

    @Test
    void duplicateEmployeeNumberReturnsConflictWithoutCreatingAccount() {
        when(repository.existsByEmployeeNumber("EMP001")).thenReturn(true);
        var exception = assertThrows(ResponseStatusException.class, () -> service.createEmployee(request));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Employee number already exists", exception.getReason());
        verifyNoInteractions(userService);
        verify(repository, never()).save(any());
    }

    @Test
    void duplicateEmailPropagatesConflictWithoutSavingEmployee() {
        var conflict = new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        when(userService.createEmployeeAccount(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(conflict);
        assertSame(conflict, assertThrows(ResponseStatusException.class, () -> service.createEmployee(request)));
        verify(repository, never()).save(any());
    }

    @Test
    void retrievesAllEmployees() {
        var employees = List.of(employee, new Employee());
        when(repository.findAll()).thenReturn(employees);
        assertEquals(employees, service.findAll());
        verify(repository).findAll();
    }

    @Test
    void retrievesEmptyEmployeeList() {
        when(repository.findAll()).thenReturn(List.of());
        assertTrue(service.findAll().isEmpty());
    }

    @Test
    void retrievesEmployeeById() {
        when(repository.findById(id)).thenReturn(Optional.of(employee));
        assertSame(employee, service.getEmployeeById(id));
    }

    @Test
    void missingEmployeeReturnsNotFound() {
        var exception = assertThrows(ResponseStatusException.class, () -> service.getEmployeeById(id));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Employee not found", exception.getReason());
    }

    @Test
    void updatesDetailsAndPreservesAccountIdentity() {
        when(repository.findById(id)).thenReturn(Optional.of(employee));
        when(repository.save(employee)).thenReturn(employee);
        var update = new UpdateEmployeeRequest(" Grace ", " Hopper ", null, hireDate.plusDays(1));
        assertSame(employee, service.updateEmployee(id, update));
        assertAll(
                () -> assertEquals("Grace", user.getFirstName()),
                () -> assertEquals("Hopper", user.getLastName()),
                () -> assertNull(employee.getPhoneNumber()),
                () -> assertEquals(update.hireDate(), employee.getHireDate()),
                () -> assertEquals("EMP001", employee.getEmployeeNumber()),
                () -> assertEquals("ada@example.com", user.getEmail()),
                () -> assertEquals("hashed", user.getPasswordHash()),
                () -> assertEquals(Role.EMPLOYEE, user.getRole()));
        verify(repository).save(employee);
        verifyNoInteractions(userService);
    }

    @Test
    void updateMissingEmployeeDoesNotSave() {
        var exception = assertThrows(ResponseStatusException.class, () -> service.updateEmployee(id,
                new UpdateEmployeeRequest("Ada", "Lovelace", null, hireDate)));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void deletesEmployeeAndFlushesBeforeDeletingUserAccount() {
        when(repository.findById(id)).thenReturn(Optional.of(employee));
        service.deleteEmployee(id);
        var order = inOrder(repository, userService);
        order.verify(repository).delete(employee);
        order.verify(repository).flush();
        order.verify(userService).deleteById(user.getId());
    }

    @Test
    void deleteMissingEmployeeDoesNotDeleteAccount() {
        var exception = assertThrows(ResponseStatusException.class, () -> service.deleteEmployee(id));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(repository, never()).delete(any());
        verify(repository, never()).flush();
        verifyNoInteractions(userService);
    }

    @Test
    void failedEmployeeFlushDoesNotDeleteUserAccount() {
        when(repository.findById(id)).thenReturn(Optional.of(employee));
        doThrow(new IllegalStateException("flush failed")).when(repository).flush();
        assertThrows(IllegalStateException.class, () -> service.deleteEmployee(id));
        verifyNoInteractions(userService);
    }

    @Test
    void mapsEveryPublicResponseField() {
        assertEquals(new EmployeeResponse(id, user.getId(), "Ada", "Lovelace", "ada@example.com",
                "EMP001", "2045550100", hireDate), service.toResponse(employee));
    }
}
