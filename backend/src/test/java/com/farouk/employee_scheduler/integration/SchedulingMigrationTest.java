package com.farouk.employee_scheduler.integration;

import com.farouk.employee_scheduler.bootstrap.ShiftSchemaValidator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual migration scripts against the inspected legacy table shape. */
@EnabledIfSystemProperty(named = "availability.test.jdbc-url", matches = ".+")
class SchedulingMigrationTest {
    private Connection connection;
    private static final String EMPLOYEE = "11111111-1111-1111-1111-111111111111";
    private static final String SHIFT = "22222222-2222-2222-2222-222222222222";
    private static final String SCHEDULE = "33333333-3333-3333-3333-333333333333";

    @BeforeEach
    void legacySchema() throws Exception {
        String url = System.getProperty("availability.test.jdbc-url");
        assertTrue(url.matches("jdbc:postgresql://[^/]+/availability_test(?:\\?.*)?"));
        connection = DriverManager.getConnection(url, System.getProperty("availability.test.jdbc-user", "postgres"), System.getProperty("availability.test.jdbc-password", ""));
        execute("create schema migration_stage1; set search_path to migration_stage1, public");
        execute("create table employees (id uuid primary key)");
        execute("create table shift (end_time time(0), start_time time(0), employee_id uuid references employees(id), id uuid primary key, day varchar(255))");
        execute("insert into employees values ('" + EMPLOYEE + "')");
    }

    @AfterEach
    void cleanup() throws Exception {
        if (connection != null) {
            try { execute("rollback; set search_path to public; drop schema if exists migration_stage1 cascade"); }
            finally { connection.close(); }
        }
    }

    @Test
    void emptyLegacyTableMigratesAndScriptsCanBeRerun() throws Exception {
        migrate("001_expand_weekly_schedules.sql"); migrate("002_finalize_dated_shifts.sql");
        migrate("001_expand_weekly_schedules.sql"); migrate("002_finalize_dated_shifts.sql");
        assertEquals(5, scalar("select count(*) from pg_attribute where attrelid='migration_stage1.shift'::regclass and attname in ('employee_id','weekly_schedule_id','shift_date','start_time','end_time') and attnotnull"));
        assertEquals(1, scalar("select count(*) from pg_constraint where conrelid='migration_stage1.shift'::regclass and conname='ex_shift_employee_time'"));
        assertDoesNotThrow(() -> new ShiftSchemaValidator(new JdbcTemplate(new SingleConnectionDataSource(connection, true))).afterSingletonsInstantiated());
    }

    @Test
    void unmappedLegacyRowsStopFinalizationWithoutDataLoss() throws Exception {
        seedLegacy(); migrate("001_expand_weekly_schedules.sql");
        assertThrows(SQLException.class, () -> migrate("002_finalize_dated_shifts.sql"));
        assertEquals(1, scalar("select count(*) from shift where id='" + SHIFT + "' and day='Monday' and start_time='09:00' and weekly_schedule_id is null and shift_date is null"));
        assertEquals(0, scalar("select count(*) from pg_constraint where conrelid='migration_stage1.shift'::regclass and conname='ex_shift_employee_time'"));
        assertThrows(IllegalStateException.class, () -> new ShiftSchemaValidator(new JdbcTemplate(new SingleConnectionDataSource(connection, true))).afterSingletonsInstantiated());
    }

    @Test
    void explicitBackfillPreservesLegacyIdsTimesAndWeekdayAuditData() throws Exception {
        seedLegacy(); migrate("001_expand_weekly_schedules.sql");
        execute("insert into weekly_schedule (id,week_start_date) values ('" + SCHEDULE + "','2026-10-26')");
        execute("update shift set shift_date='2026-10-26', weekly_schedule_id='" + SCHEDULE + "' where id='" + SHIFT + "'");
        migrate("002_finalize_dated_shifts.sql");
        assertEquals(1, scalar("select count(*) from shift where id='" + SHIFT + "' and day='Monday' and start_time='09:00' and end_time='12:00' and employee_id='" + EMPLOYEE + "' and weekly_schedule_id='" + SCHEDULE + "' and shift_date='2026-10-26'"));
        var conflict = assertThrows(SQLException.class, () -> execute("insert into shift (id,employee_id,weekly_schedule_id,shift_date,start_time,end_time) values (gen_random_uuid(),'" + EMPLOYEE + "','" + SCHEDULE + "','2026-10-26','10:00','13:00')"));
        assertEquals("23P01", conflict.getSQLState());
    }

    @Test
    void invalidLegacyTimesCauseRollbackRatherThanDeletion() throws Exception {
        seedLegacy(); migrate("001_expand_weekly_schedules.sql");
        execute("insert into weekly_schedule (id,week_start_date) values ('" + SCHEDULE + "','2026-10-26')");
        execute("update shift set shift_date='2026-10-26',weekly_schedule_id='" + SCHEDULE + "',end_time=start_time");
        assertThrows(SQLException.class, () -> migrate("002_finalize_dated_shifts.sql"));
        assertEquals(1, scalar("select count(*) from shift where start_time=end_time"));
    }

    @Test
    void databaseEnforcesMondayUniqueWeekAndDraftOnly() throws Exception {
        migrate("001_expand_weekly_schedules.sql"); migrate("002_finalize_dated_shifts.sql");
        execute("insert into weekly_schedule (id,week_start_date) values ('" + SCHEDULE + "','2026-10-26')");
        assertEquals("23505", assertThrows(SQLException.class, () -> execute("insert into weekly_schedule (id,week_start_date) values (gen_random_uuid(),'2026-10-26')")).getSQLState());
        assertEquals("23514", assertThrows(SQLException.class, () -> execute("insert into weekly_schedule (id,week_start_date) values (gen_random_uuid(),'2026-10-27')")).getSQLState());
        assertEquals("23514", assertThrows(SQLException.class, () -> execute("insert into weekly_schedule (id,week_start_date,status) values (gen_random_uuid(),'2026-11-02','PUBLISHED')")).getSQLState());
    }

    private void seedLegacy() throws Exception {
        execute("insert into shift (id,employee_id,day,start_time,end_time) values ('" + SHIFT + "','" + EMPLOYEE + "','Monday','09:00','12:00')");
    }

    private void migrate(String resource) throws Exception {
        String sql = new ClassPathResource("db/migration/" + resource).getContentAsString(StandardCharsets.UTF_8);
        try { execute(sql); }
        catch (SQLException exception) { execute("rollback"); throw exception; }
    }

    private void execute(String sql) throws SQLException { try (var statement = connection.createStatement()) { statement.execute(sql); } }
    private long scalar(String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) { result.next(); return result.getLong(1); }
    }
}
