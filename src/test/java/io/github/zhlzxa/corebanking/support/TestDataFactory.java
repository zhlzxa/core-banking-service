package io.github.zhlzxa.corebanking.support;

import java.math.BigDecimal;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Inserts reference data for integration tests directly through SQL, so that tests do not depend on
 * the production code paths they are verifying.
 */
@TestComponent
public class TestDataFactory {

    public static final String ISSUER = "https://issuer.test";

    private final JdbcClient jdbc;

    public TestDataFactory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long createUser(String subject, String role, String status) {
        return jdbc.sql("""
                        INSERT INTO users (identity_issuer, identity_subject, display_name, role, status)
                        VALUES (:issuer, :subject, :displayName, :role, :status)
                        RETURNING id
                        """)
                .param("issuer", ISSUER)
                .param("subject", subject)
                .param("displayName", "Test user " + subject)
                .param("role", role)
                .param("status", status)
                .query(Long.class)
                .single();
    }

    public long createCustomer(String subject) {
        return createUser(subject, "CUSTOMER", "ACTIVE");
    }

    public void createAccount(long accountId, Long ownerUserId, String currency, String balance) {
        jdbc.sql("""
                        INSERT INTO accounts (id, user_id, currency, balance)
                        VALUES (:id, :userId, :currency, :balance)
                        """)
                .param("id", accountId)
                .param("userId", ownerUserId)
                .param("currency", currency)
                .param("balance", new BigDecimal(balance))
                .update();
    }

    public BigDecimal balanceOf(long accountId) {
        return jdbc.sql("SELECT balance FROM accounts WHERE id = :id")
                .param("id", accountId)
                .query(BigDecimal.class)
                .single();
    }

    public int count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }

    /** Number of transactions whose ledger debits and credits do not net to zero. */
    public int unbalancedTransactions() {
        return jdbc.sql("""
                        SELECT count(*) FROM (
                            SELECT transaction_id
                            FROM ledger_entries
                            GROUP BY transaction_id
                            HAVING sum(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END) <> 0
                        ) unbalanced
                        """).query(Integer.class).single();
    }
}
