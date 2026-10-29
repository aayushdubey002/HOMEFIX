package com.homefix.api.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Locale;

@Component
public class ProblemImageSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public ProblemImageSchemaMigration(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        String databaseProduct;
        try (var connection = dataSource.getConnection()) {
            databaseProduct = connection.getMetaData().getDatabaseProductName();
        }
        if (!"MySQL".equalsIgnoreCase(databaseProduct)) {
            return;
        }

        String columnType = jdbcTemplate.query(
                """
                        SELECT DATA_TYPE
                        FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'bookings'
                          AND COLUMN_NAME = 'problem_image_data'
                        """,
                resultSet -> resultSet.next() ? resultSet.getString(1) : null);

        if (columnType == null) {
            throw new IllegalStateException(
                    "The bookings.problem_image_data column is missing after schema initialization.");
        }

        String normalizedType = columnType.toLowerCase(Locale.ROOT);
        if (!"mediumblob".equals(normalizedType) && !"longblob".equals(normalizedType)) {
            jdbcTemplate.execute(
                    "ALTER TABLE bookings MODIFY COLUMN problem_image_data MEDIUMBLOB");
        }
    }
}
