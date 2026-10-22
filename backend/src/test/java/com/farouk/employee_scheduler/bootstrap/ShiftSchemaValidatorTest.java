package com.farouk.employee_scheduler.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShiftSchemaValidatorTest {
    @Test
    void refusesToRunWithMissingNativeConstraints() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> new ShiftSchemaValidator(jdbc).afterSingletonsInstantiated());
    }

    @Test
    void acceptsCompletedMigration() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        assertDoesNotThrow(() -> new ShiftSchemaValidator(jdbc).afterSingletonsInstantiated());
    }
}
