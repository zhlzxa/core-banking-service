package io.github.zhlzxa.corebanking.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for integration tests that run against a real PostgreSQL instance.
 *
 * <p>A single container is started once per JVM and shared by every test class, so the Spring
 * context can be cached across classes; every subclass therefore shares the same configuration,
 * including MockMvc. The schema is created by Flyway exactly as in production;
 * tests never rely on a hand-written schema. Every test starts from empty tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({DatabaseCleaner.class, TestDataFactory.class})
public abstract class AbstractIntegrationIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected DatabaseCleaner databaseCleaner;

    @BeforeEach
    void resetDatabase() {
        databaseCleaner.truncateAllTables();
    }
}
