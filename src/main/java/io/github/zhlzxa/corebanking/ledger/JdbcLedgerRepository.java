package io.github.zhlzxa.corebanking.ledger;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLedgerRepository implements LedgerRepository {

    private final JdbcClient jdbc;

    JdbcLedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(LedgerEntry entry) {
        jdbc.sql("""
                        INSERT INTO ledger_entries (transaction_id, account_id, direction, amount, currency)
                        VALUES (:transactionId, :accountId, :direction, :amount, :currency)
                        """)
                .param("transactionId", entry.transactionId())
                .param("accountId", entry.accountId())
                .param("direction", entry.direction().name())
                .param("amount", entry.amount())
                .param("currency", entry.currency())
                .update();
    }
}
