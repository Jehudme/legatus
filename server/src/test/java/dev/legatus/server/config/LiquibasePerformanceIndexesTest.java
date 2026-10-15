package dev.legatus.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.IntegrationTest;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration test verifying that Liquibase applied all performance indexes on H2 schema.
 */
@IntegrationTest
class LiquibasePerformanceIndexesTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Should verify that composite performance indexes exist in database schema")
    void testPerformanceIndexesExist() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        List<String> indexes = jdbcTemplate.query(
            "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES",
            (rs, rowNum) -> rs.getString("INDEX_NAME").toUpperCase()
        );

        assertThat(indexes).contains(
            "IDX_TASKS_STATUS_PRIORITY",
            "IDX_TASKS_ASSIGNED_WORKER_STATUS",
            "IDX_WORKERS_STATE_DRAINING"
        );
    }
}
