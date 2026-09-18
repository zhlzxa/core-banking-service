package io.github.zhlzxa.corebanking.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import io.github.zhlzxa.corebanking.ledger.EntryDirection;
import io.github.zhlzxa.corebanking.ledger.LedgerRepository;
import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestSecurityContexts;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private TestDataFactory data;

    private long alice;

    @BeforeEach
    void seedAccounts() {
        alice = data.createCustomer("alice-sub");
        long bob = data.createCustomer("bob-sub");
        data.createAccount(1, alice, "HKD", "1000");
        data.createAccount(2, bob, "HKD", "500");
        SecurityContextHolder.setContext(TestSecurityContexts.customer(alice, "bank.transfer"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void failureWhilePostingTheCreditLegRollsBackEverything() {
        doThrow(new IllegalStateException("Simulated storage failure"))
                .when(ledgerRepository)
                .append(argThat(entry -> entry.direction() == EntryDirection.CREDIT));

        assertThatThrownBy(() -> transferService.transfer(
                        new TransferCommand(alice, "req-rollback", 1, 2, new BigDecimal("100.00"), "HKD")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(data.balanceOf(1)).isEqualByComparingTo("1000");
        assertThat(data.balanceOf(2)).isEqualByComparingTo("500");
        assertThat(data.count("transactions")).isZero();
        assertThat(data.count("ledger_entries")).isZero();
    }
}
