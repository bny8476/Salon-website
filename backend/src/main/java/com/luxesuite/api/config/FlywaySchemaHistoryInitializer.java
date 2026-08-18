package com.luxesuite.api.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Pre-creates the Flyway schema history table using TiDB-compatible DDL.
 * 
 * TiDB does not support CREATE TABLE ... SELECT, which Flyway's MySQL module
 * uses internally when creating the flyway_schema_history table during baseline.
 * This initializer creates the table manually before Flyway runs, avoiding the issue.
 */
@Configuration
public class FlywaySchemaHistoryInitializer {

    @Bean
    public FlywayConfigurationCustomizer flywayConfigurationCustomizer(DataSource dataSource) {
        return configuration -> {
            try {
                ensureSchemaHistoryTable(dataSource, configuration.getTable());
            } catch (Exception e) {
                // Log but don't fail — let Flyway handle it if possible
                System.err.println("Warning: Could not pre-create Flyway schema history table: " + e.getMessage());
            }
        };
    }

    private void ensureSchemaHistoryTable(DataSource dataSource, String tableName) throws Exception {
        if (tableName == null || tableName.isBlank()) {
            tableName = "flyway_schema_history";
        }

        try (Connection conn = dataSource.getConnection()) {
            // Check if the table already exists
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
                if (rs.next()) {
                    return; // Table already exists, nothing to do
                }
            }

            // Create the flyway_schema_history table using TiDB-compatible DDL
            // This matches Flyway's expected schema but avoids CREATE TABLE ... SELECT
            String createTableSql = String.format(
                "CREATE TABLE `%s` (" +
                "    `installed_rank` INT NOT NULL," +
                "    `version` VARCHAR(50)," +
                "    `description` VARCHAR(200) NOT NULL," +
                "    `type` VARCHAR(20) NOT NULL," +
                "    `script` VARCHAR(1000) NOT NULL," +
                "    `checksum` INT," +
                "    `installed_by` VARCHAR(100) NOT NULL," +
                "    `installed_on` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "    `execution_time` INT NOT NULL," +
                "    `success` TINYINT(1) NOT NULL," +
                "    PRIMARY KEY (`installed_rank`)," +
                "    INDEX `%s_s_idx` (`success`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
                tableName, tableName
            );

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSql);
                System.out.println("Pre-created Flyway schema history table: " + tableName);
            }
        }
    }
}
