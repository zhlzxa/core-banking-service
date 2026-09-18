package io.github.zhlzxa.corebanking.ledger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Verifies that the database itself enforces the ledger invariants, independent of Java code. */
class LedgerSchemaIT extends AbstractIntegrationIT {

    @Autowired
    private JdbcClient jdbc;

    private long transactionId;

    @BeforeEach
    void seedOnePostedTransfer() {
        jdbc.sql("INSERT INTO accounts (id, currency, balance) VALUES (1, 'HKD', 900), (2, 'HKD', 100)")
                .update();
        transactionId = jdbc.sql("""
                        INSERT INTO transactions
                            (request_id, transaction_type, status, from_account_id, to_account_id, amount, currency)
                        VALUES ('req-1', 'TRANSFER', 'COMPLETED', 1, 2, 100, 'HKD')
                        RETURNING id
                        """).query(Long.class).single();
        jdbc.sql("""
                        INSERT INTO ledger_entries (transaction_id, account_id, direction, amount, currency)
                        VALUES (:tx, 1, 'DEBIT', 100, 'HKD'), (:tx, 2, 'CREDIT', 100, 'HKD')
                        """).param("tx", transactionId).update();
    }

    @Test
    void ledgerEntriesCannotBeUpdated() {
        assertThatThrownBy(
                        () -> jdbc.sql("UPDATE ledger_entries SET amount = 1").update())
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("append-only");
    }

    @Test
    void ledgerEntriesCannotBeDeleted() {
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM ledger_entries").update())
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("append-only");
    }

    @Test
    void accountBalanceCannotBecomeNegative() {
        assertThatThrownBy(() -> jdbc.sql("UPDATE accounts SET balance = -0.01 WHERE id = 1")
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_balance_non_negative");
    }

    @Test
    void requestIdIsUnique() {
        assertThatThrownBy(() -> jdbc.sql("""
                                INSERT INTO transactions
                                    (request_id, transaction_type, status, from_account_id, to_account_id, amount, currency)
                                VALUES ('req-1', 'TRANSFER', 'PENDING', 2, 1, 5, 'HKD')
                                """).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_transactions_request_id");
    }

    @Test
    void transferToTheSameAccountIsRejected() {
        assertThatThrownBy(() -> jdbc.sql("""
                                INSERT INTO transactions
                                    (request_id, transaction_type, status, from_account_id, to_account_id, amount, currency)
                                VALUES ('req-2', 'TRANSFER', 'PENDING', 1, 1, 5, 'HKD')
                                """).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_distinct_accounts");
    }

    @Test
    void sameLegCannotBePostedTwice() {
        assertThatThrownBy(() -> jdbc.sql("""
                                INSERT INTO ledger_entries (transaction_id, account_id, direction, amount, currency)
                                VALUES (:tx, 1, 'DEBIT', 100, 'HKD')
                                """).param("tx", transactionId).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_ledger_entries_leg");
    }
}
