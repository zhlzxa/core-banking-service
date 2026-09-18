package io.github.zhlzxa.corebanking.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import io.github.zhlzxa.corebanking.ledger.EntryDirection;
import io.github.zhlzxa.corebanking.ledger.LedgerRepository;
import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Proves atomicity against a real database: a failure after the debit has already been written must
 * leave no trace of the transfer at all.
 */
class TransferRollbackIT extends AbstractIntegrationIT {

    @MockitoSpyBean
    private LedgerRepository ledgerRepository;

    @Autowired
    private TransferService transferService;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seedAccounts() {
        jdbc.sql("INSERT INTO accounts (id, currency, balance) VALUES (1, 'HKD', 1000), (2, 'HKD', 500)")
                .update();
    }

    @Test
    void failureWhilePostingTheCreditLegRollsBackEverything() {
        doThrow(new IllegalStateException("Simulated storage failure"))
                .when(ledgerRepository)
                .append(argThat(entry -> entry.direction() == EntryDirection.CREDIT));

        assertThatThrownBy(() -> transferService.transfer(
                        new TransferCommand("req-rollback", 1, 2, new BigDecimal("100.00"), "HKD")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(balanceOf(1)).isEqualByComparingTo("1000");
        assertThat(balanceOf(2)).isEqualByComparingTo("500");
        assertThat(count("transactions")).isZero();
        assertThat(count("ledger_entries")).isZero();
    }

    private BigDecimal balanceOf(long accountId) {
        return jdbc.sql("SELECT balance FROM accounts WHERE id = :id")
                .param("id", accountId)
                .query(BigDecimal.class)
                .single();
    }

    private int count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }
}
