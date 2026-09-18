package io.github.zhlzxa.corebanking.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BankTransactionTest {

    private final BankTransaction stored = new BankTransaction(
            7,
            "req-1",
            TransactionType.TRANSFER,
            TransactionStatus.COMPLETED,
            1,
            2,
            new BigDecimal("100.0000"),
            "HKD",
            Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void sameInstructionIgnoresAmountScale() {
        assertThat(stored.isSameInstructionAs(instruction(1, 2, "100.00", "HKD")))
                .isTrue();
    }

    @Test
    void differentAccountsAmountOrCurrencyIsADifferentInstruction() {
        assertThat(stored.isSameInstructionAs(instruction(2, 1, "100.00", "HKD")))
                .isFalse();
        assertThat(stored.isSameInstructionAs(instruction(1, 3, "100.00", "HKD")))
                .isFalse();
        assertThat(stored.isSameInstructionAs(instruction(1, 2, "100.01", "HKD")))
                .isFalse();
        assertThat(stored.isSameInstructionAs(instruction(1, 2, "100.00", "USD")))
                .isFalse();
    }

    private static NewTransaction instruction(long from, long to, String amount, String currency) {
        return new NewTransaction("req-1", TransactionType.TRANSFER, from, to, new BigDecimal(amount), currency);
    }
}
