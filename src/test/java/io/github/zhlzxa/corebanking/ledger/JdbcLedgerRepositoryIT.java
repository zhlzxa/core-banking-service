package io.github.zhlzxa.corebanking.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcLedgerRepositoryIT extends AbstractIntegrationIT {

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private JdbcClient jdbc;

    private long transactionId;

    @BeforeEach
    void seedTransaction() {
        jdbc.sql("INSERT INTO accounts (id, currency, balance) VALUES (1, 'HKD', 0), (2, 'HKD', 0)")
                .update();
        transactionId = jdbc.sql("""
                        INSERT INTO transactions
                            (request_id, transaction_type, status, from_account_id, to_account_id, amount, currency)
                        VALUES ('req-1', 'TRANSFER', 'PENDING', 1, 2, 25, 'HKD')
                        RETURNING id
                        """).query(Long.class).single();
    }

    @Test
    void appendsBothLegsOfAPosting() {
        BigDecimal amount = new BigDecimal("25.00");

        ledgerRepository.append(LedgerEntry.debit(transactionId, 1, amount, "HKD"));
        ledgerRepository.append(LedgerEntry.credit(transactionId, 2, amount, "HKD"));

        List<String> legs =
                jdbc.sql("""
                        SELECT account_id || ':' || direction || ':' || amount
                        FROM ledger_entries WHERE transaction_id = :tx ORDER BY account_id
                        """).param("tx", transactionId).query(String.class).list();
        assertThat(legs).containsExactly("1:DEBIT:25.0000", "2:CREDIT:25.0000");
    }
}
