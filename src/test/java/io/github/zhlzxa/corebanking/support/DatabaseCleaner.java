package io.github.zhlzxa.corebanking.support;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Empties every application table between tests.
 *
 * <p>The table list is read from the catalog instead of being hard-coded, so new migrations are
 * picked up automatically. Flyway's history table is preserved. {@code TRUNCATE} bypasses
 * row-level triggers, which lets tests reset append-only tables.
 */
@TestComponent
public class DatabaseCleaner {

    private final JdbcClient jdbcClient;

    public DatabaseCleaner(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void truncateAllTables() {
        List<String> tables = jdbcClient.sql("""
                        SELECT quote_ident(tablename)
                        FROM pg_tables
                        WHERE schemaname = 'public'
                          AND tablename <> 'flyway_schema_history'
                        """).query(String.class).list();
        if (!tables.isEmpty()) {
            jdbcClient
                    .sql("TRUNCATE " + tables.stream().collect(Collectors.joining(", ")) + " RESTART IDENTITY CASCADE")
                    .update();
        }
    }
}
