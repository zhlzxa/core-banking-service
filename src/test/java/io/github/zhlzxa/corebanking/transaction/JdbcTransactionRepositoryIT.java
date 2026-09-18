package io.github.zhlzxa.corebanking.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.JdbcUpdateAffectedIncorrectNumberOfRowsException;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcTransactionRepositoryIT extends AbstractIntegrationIT {

    private static final NewTransaction TRANSFER =
            new NewTransaction("req-1", null, TransactionType.TRANSFER, 1, 2, new BigDecimal("25.00"), "HKD");

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seedAccounts() {
        jdbc.sql("INSERT INTO accounts (id, currency, balance) VALUES (1, 'HKD', 0), (2, 'HKD', 0)")
                .update();
    }

    @Test
    void insertsPendingTransactionAndReadsItBack() {
        long id = transactionRepository.insertPendingIfAbsent(TRANSFER).orElseThrow();

        BankTransaction stored = transactionRepository.findById(id).orElseThrow();
        assertThat(stored.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(stored.requestId()).isEqualTo("req-1");
        assertThat(stored.amount()).isEqualByComparingTo("25.00");
        assertThat(stored.createdAt()).isNotNull();
        assertThat(transactionRepository.findByRequestId("req-1")).contains(stored);
    }

    @Test
    void secondInsertWithSameRequestIdIsIgnored() {
        long first = transactionRepository.insertPendingIfAbsent(TRANSFER).orElseThrow();

        assertThat(transactionRepository.insertPendingIfAbsent(TRANSFER)).isEmpty();
        assertThat(transactionRepository.findByRequestId("req-1"))
                .hasValueSatisfying(tx -> assertThat(tx.id()).isEqualTo(first));
    }

    @Test
    void updatesStatus() {
        long id = transactionRepository.insertPendingIfAbsent(TRANSFER).orElseThrow();

        transactionRepository.updateStatus(id, TransactionStatus.COMPLETED);

        assertThat(transactionRepository.findById(id).orElseThrow().status()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void updatingUnknownTransactionFails() {
        assertThatThrownBy(() -> transactionRepository.updateStatus(999, TransactionStatus.COMPLETED))
                .isInstanceOf(JdbcUpdateAffectedIncorrectNumberOfRowsException.class);
    }

    @Test
    void unknownLookupsReturnEmpty() {
        assertThat(transactionRepository.findById(999)).isEmpty();
        assertThat(transactionRepository.findByRequestId("missing")).isEmpty();
    }
}
