package io.github.zhlzxa.corebanking.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
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

    /**
     * Creates a customer account, or an internal account with code {@code TEST-<id>} when no owner
     * is given.
     */
    public void createAccount(long accountId, Long ownerUserId, String currency, String balance) {
        jdbc.sql("""
                        INSERT INTO accounts (id, user_id, account_type, code, currency, balance)
                        VALUES (:id, :userId, :type, :code, :currency, :balance)
                        """)
                .param("id", accountId)
                .param("userId", ownerUserId)
                .param("type", ownerUserId == null ? "INTERNAL" : "CUSTOMER")
                .param("code", ownerUserId == null ? "TEST-" + accountId : null)
                .param("currency", currency)
                .param("balance", new BigDecimal(balance))
                .update();
    }

    /** Creates a customer account owned by a new customer of its own. */
    public long createCustomerAccount(long accountId, String currency, String balance) {
        long owner = createCustomer("owner-of-" + accountId);
        createAccount(accountId, owner, currency, balance);
        return owner;
    }

    /** Creates an internal account such as a cash account; returns its generated id. */
    public long createInternalAccount(String code, String currency) {
        return jdbc.sql("""
                        INSERT INTO accounts (account_type, code, currency, balance)
                        VALUES ('INTERNAL', :code, :currency, 0)
                        RETURNING id
                        """)
                .param("code", code)
                .param("currency", currency)
                .query(Long.class)
                .single();
    }

    /**
     * Inserts a completed HKD transfer row with an explicit creation time, bypassing the service.
     * Used to build histories with precisely controlled ordering; balances are not touched.
     */
    public long insertCompletedTransfer(long fromAccountId, long toAccountId, String amount, Instant createdAt) {
        return jdbc.sql("""
                        INSERT INTO transactions
                            (request_id, transaction_type, status, from_account_id, to_account_id,
                             amount, currency, created_at)
                        VALUES (:requestId, 'TRANSFER', 'COMPLETED', :from, :to, :amount, 'HKD', :createdAt)
                        RETURNING id
                        """)
                .param("requestId", UUID.randomUUID().toString())
                .param("from", fromAccountId)
                .param("to", toAccountId)
                .param("amount", new BigDecimal(amount))
                .param("createdAt", createdAt.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .single();
    }

    public void setStatus(long accountId, String status, String reason) {
        jdbc.sql("UPDATE accounts SET status = :status, status_reason = :reason WHERE id = :id")
                .param("id", accountId)
                .param("status", status)
                .param("reason", reason)
                .update();
    }

    public void setLimits(long accountId, String perTransaction, String daily) {
        jdbc.sql(
                        "UPDATE accounts SET per_transaction_limit = :perTransaction, daily_transfer_limit = :daily WHERE id = :id")
                .param("id", accountId)
                .param("perTransaction", perTransaction == null ? null : new BigDecimal(perTransaction))
                .param("daily", daily == null ? null : new BigDecimal(daily))
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
