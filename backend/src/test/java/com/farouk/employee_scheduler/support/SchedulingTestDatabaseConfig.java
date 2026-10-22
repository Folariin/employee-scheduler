package com.farouk.employee_scheduler.support;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ClassPathResource;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

@TestConfiguration(proxyBeanMethods = false)
public class SchedulingTestDatabaseConfig {
    @Bean
    @DependsOn("entityManagerFactory")
    InitializingBean installPostgresShiftConstraints(DataSource dataSource) {
        return () -> {
            String sql = new ClassPathResource("db/migration/002_finalize_dated_shifts.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute(sql);
                statement.execute(new ClassPathResource("db/migration/003_publish_weekly_schedules.sql")
                        .getContentAsString(StandardCharsets.UTF_8));
            }
        };
    }
}
