package io.github.zhlzxa.corebanking.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.JdbcUpdateAffectedIncorrectNumberOfRowsException;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcAccountRepositoryIT extends AbstractIntegrationIT {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seedAccount() {
        jdbc.sql("INSERT INTO accounts (id, currency, balance) VALUES (10, 'HKD', 100.00)")
                .update();
    }

    @Test
    void findsAccountById() {
        assertThat(accountRepository.findById(10)).hasValueSatisfying(account -> {
            assertThat(account.currency()).isEqualTo("HKD");
            assertThat(account.balance()).isEqualByComparingTo("100.00");
        });
        assertThat(accountRepository.findByIdForUpdate(10)).isPresent();
    }

    @Test
    void returnsEmptyForUnknownAccount() {
        assertThat(accountRepository.findById(999)).isEmpty();
        assertThat(accountRepository.findByIdForUpdate(999)).isEmpty();
    }

    @Test
    void debitAndCreditAdjustBalance() {
        accountRepository.debit(10, new BigDecimal("30.00"));
        accountRepository.credit(10, new BigDecimal("5.50"));

        assertThat(accountRepository.findById(10).orElseThrow().balance()).isEqualByComparingTo("75.50");
    }

    @Test
    void debitNeverOverdrawsEvenWithoutServiceLevelCheck() {
        assertThatThrownBy(() -> accountRepository.debit(10, new BigDecimal("100.01")))
                .isInstanceOf(JdbcUpdateAffectedIncorrectNumberOfRowsException.class);

        assertThat(accountRepository.findById(10).orElseThrow().balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void creditToUnknownAccountFails() {
        assertThatThrownBy(() -> accountRepository.credit(999, BigDecimal.ONE))
                .isInstanceOf(JdbcUpdateAffectedIncorrectNumberOfRowsException.class);
    }
}
