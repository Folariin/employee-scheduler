package com.farouk.employee_scheduler.bootstrap;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Hibernate schema validation does not verify PostgreSQL exclusion constraints. */
@Component
@DependsOn("entityManagerFactory")
public class ShiftSchemaValidator implements SmartInitializingSingleton {
    private final JdbcTemplate jdbc;

    public ShiftSchemaValidator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void afterSingletonsInstantiated() {
        Boolean ready = jdbc.queryForObject("""
                select exists (select 1 from pg_constraint
                    where conrelid = to_regclass('shift') and conname = 'ex_shift_employee_time' and contype = 'x')
                  and exists (select 1 from pg_constraint
                    where conrelid = to_regclass('weekly_schedule') and conname = 'uk_weekly_schedule_start' and contype = 'u')
                  and exists (select 1 from pg_constraint
                    where conrelid = to_regclass('weekly_schedule') and conname = 'ck_weekly_schedule_status'
                      and contype = 'c' and convalidated
                      and pg_get_constraintdef(oid) like '%DRAFT%'
                      and pg_get_constraintdef(oid) like '%PUBLISHED%')
                  and not exists (select 1 from pg_constraint
                    where conrelid = to_regclass('weekly_schedule') and contype = 'c'
                      and conname <> 'ck_weekly_schedule_status'
                      and conkey = ARRAY[(select attnum from pg_attribute
                        where attrelid = to_regclass('weekly_schedule') and attname = 'status')]::smallint[])
                  and (select count(*) from pg_attribute where attrelid = to_regclass('shift')
                    and attname in ('employee_id','weekly_schedule_id','shift_date','start_time','end_time')
                    and attnotnull and not attisdropped) = 5
                """, Boolean.class);
        if (!Boolean.TRUE.equals(ready)) {
            throw new IllegalStateException("Scheduling schema is incomplete. Apply db/migration/001, 002 and 003 as documented in backend/db/MIGRATION.md before starting the application.");
        }
    }
}
