package io.github.zhlzxa.corebanking.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.JdbcUpdateAffectedIncorrectNumberOfRowsException;

class JdbcAccountRepositoryIT extends AbstractIntegrationIT {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TestDataFactory data;

    @BeforeEach
    void seedAccount() {
        data.createCustomerAccount(10, "HKD", "100.00");
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
    void internalAccountsMayGoNegative() {
        long cash = data.createInternalAccount("CASH-TEST", "HKD");

        accountRepository.debit(cash, new BigDecimal("250.00"));

        assertThat(accountRepository.findById(cash).orElseThrow().balance()).isEqualByComparingTo("-250.00");
        assertThat(accountRepository.findInternalAccountId("CASH-TEST")).isEqualTo(cash);
    }

    @Test
    void unknownInternalAccountCodeIsNotFound() {
        assertThatThrownBy(() -> accountRepository.findInternalAccountId("NO-SUCH-CODE"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void creditToUnknownAccountFails() {
        assertThatThrownBy(() -> accountRepository.credit(999, BigDecimal.ONE))
                .isInstanceOf(JdbcUpdateAffectedIncorrectNumberOfRowsException.class);
    }

    @Test
    void statusAndReasonAreStoredTogether() {
        accountRepository.updateStatus(10, AccountStatus.FROZEN, StatusReason.COURT_ORDER);

        Account frozen = accountRepository.findById(10).orElseThrow();
        assertThat(frozen.status()).isEqualTo(AccountStatus.FROZEN);
        assertThat(frozen.statusReason()).isEqualTo(StatusReason.COURT_ORDER);
    }

    @Test
    void aFrozenAccountWithoutReasonIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> accountRepository.updateStatus(10, AccountStatus.FROZEN, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_frozen_has_reason");
    }

    @Test
    void limitsCanBeSetAndRemoved() {
        accountRepository.updateLimits(10, new BigDecimal("500.00"), new BigDecimal("2000.00"));
        assertThat(accountRepository.findById(10).orElseThrow().dailyTransferLimit())
                .isEqualByComparingTo("2000.00");

        accountRepository.updateLimits(10, null, null);
        Account unlimited = accountRepository.findById(10).orElseThrow();
        assertThat(unlimited.perTransactionLimit()).isNull();
        assertThat(unlimited.dailyTransferLimit()).isNull();
    }

    @Test
    void updatingAnUnknownAccountFails() {
        assertThatThrownBy(() -> accountRepository.updateStatus(999, AccountStatus.CLOSED, null))
                .isInstanceOf(JdbcUpdateAffectedIncorrectNumberOfRowsException.class);
        assertThatThrownBy(() -> accountRepository.updateLimits(999, null, null))
                .isInstanceOf(JdbcUpdateAffectedIncorrectNumberOfRowsException.class);
    }
}
