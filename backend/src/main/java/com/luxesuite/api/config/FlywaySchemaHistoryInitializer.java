package com.luxesuite.api.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Pre-creates the Flyway schema history table using TiDB-compatible DDL.
 *
 * TiDB does not support CREATE TABLE ... SELECT, which Flyway's MySQL module
 * uses internally when creating the flyway_schema_history table during baseline.
 * This initializer creates the table and inserts a baseline row manually before
 * Flyway runs, so Flyway sees an already-baselined database and skips the
 * problematic auto-creation path entirely.
 */
@Configuration
public class FlywaySchemaHistoryInitializer {

    @Bean
    public FlywayConfigurationCustomizer flywayConfigurationCustomizer(DataSource dataSource) {
        return configuration -> {
            try {
                String table = configuration.getTable();
                if (table == null || table.isBlank()) {
                    table = "flyway_schema_history";
                }
                ensureSchemaHistoryTable(dataSource, table);
            } catch (Exception e) {
                // Log but don't fail — let Flyway handle it if possible
                System.err.println("Warning: Could not pre-create Flyway schema history table: " + e.getMessage());
            }
        };
    }

    private void ensureSchemaHistoryTable(DataSource dataSource, String tableName) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            // Check if the table already exists
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(conn.getCatalog(), null, tableName, new String[]{"TABLE"})) {
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

            // Insert a baseline row so Flyway recognises the database as already baselined
            String insertBaselineSql = String.format(
                "INSERT INTO `%s` " +
                "(`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, " +
                " `installed_by`, `installed_on`, `execution_time`, `success`) " +
                "VALUES (1, '0', '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>', NULL, " +
                "        'FlywaySchemaHistoryInitializer', CURRENT_TIMESTAMP, 0, 1)",
                tableName
            );

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(insertBaselineSql);
                System.out.println("Inserted Flyway baseline row (version=0) into: " + tableName);
            }
        }
    }
}
