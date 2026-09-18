package io.github.zhlzxa.corebanking.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountTest {

    private final Account account = new Account(1, "HKD", new BigDecimal("100.00"));

    @Test
    void balanceIsSufficientUpToAndIncludingTheExactAmount() {
        assertThat(account.hasSufficientBalanceFor(new BigDecimal("99.99"))).isTrue();
        assertThat(account.hasSufficientBalanceFor(new BigDecimal("100.0000"))).isTrue();
        assertThat(account.hasSufficientBalanceFor(new BigDecimal("100.01"))).isFalse();
    }
}
