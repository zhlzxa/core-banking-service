package io.github.zhlzxa.corebanking;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The demo seed applies on top of the production migrations, can be applied again without effect,
 * and leaves a ledger in which every balance is backed by its entries.
 */
class DemoSeedIT {

    @Test
    void seedsBalancedDemoDataOnceOnly() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")) {
            postgres.start();
            DriverManagerDataSource dataSource =
                    new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration", "classpath:db/demo")
                    .load();

            flyway.migrate();
            flyway.migrate();
            JdbcClient jdbc = JdbcClient.create(dataSource);

            assertThat(count(jdbc, "users")).isEqualTo(5);
            assertThat(count(jdbc, "accounts WHERE account_type = 'CUSTOMER'")).isEqualTo(3);
            assertThat(count(jdbc, "terminals")).isEqualTo(1);
            assertThat(jdbc.sql("SELECT balance FROM accounts WHERE id = 1001")
                            .query(BigDecimal.class)
                            .single())
                    .isEqualByComparingTo("200000.00");
            assertThat(count(jdbc, """
                            accounts a WHERE a.balance <> (
                                SELECT coalesce(sum(CASE e.direction WHEN 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
                                FROM ledger_entries e WHERE e.account_id = a.id)
                            """))
                    .as("accounts whose balance differs from the sum of their entries")
                    .isZero();
        }
    }

    private static long count(JdbcClient jdbc, String from) {
        return jdbc.sql("SELECT count(*) FROM " + from).query(Long.class).single();
    }
}
