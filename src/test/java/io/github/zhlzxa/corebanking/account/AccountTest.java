package io.github.zhlzxa.corebanking.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountTest {

    private final Account account = new Account(
            1, 7L, "HKD", new BigDecimal("100.00"), AccountStatus.ACTIVE, null, new BigDecimal("50.00"), null);

    @Test
    void balanceIsSufficientUpToAndIncludingTheExactAmount() {
        assertThat(account.hasSufficientBalanceFor(new BigDecimal("99.99"))).isTrue();
        assertThat(account.hasSufficientBalanceFor(new BigDecimal("100.0000"))).isTrue();
        assertThat(account.hasSufficientBalanceFor(new BigDecimal("100.01"))).isFalse();
    }

    @Test
    void ownershipIsDecidedByInternalUserId() {
        assertThat(account.isOwnedBy(7)).isTrue();
        assertThat(account.isOwnedBy(8)).isFalse();
        assertThat(account.isCustomerAccount()).isTrue();
    }

    @Test
    void perTransactionLimitIsInclusive() {
        assertThat(account.exceedsPerTransactionLimit(new BigDecimal("50.00"))).isFalse();
        assertThat(account.exceedsPerTransactionLimit(new BigDecimal("50.01"))).isTrue();
    }

    @Test
    void internalAccountsHaveNoOwner() {
        Account internal = new Account(2, null, "HKD", BigDecimal.ZERO, AccountStatus.ACTIVE, null, null, null);

        assertThat(internal.isCustomerAccount()).isFalse();
        assertThat(internal.isOwnedBy(7)).isFalse();
    }
}
