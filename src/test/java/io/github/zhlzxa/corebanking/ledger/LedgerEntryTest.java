package io.github.zhlzxa.corebanking.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LedgerEntryTest {

    @Test
    void factoryMethodsSetDirection() {
        assertThat(LedgerEntry.debit(1, 2, BigDecimal.TEN, "HKD").direction()).isEqualTo(EntryDirection.DEBIT);
        assertThat(LedgerEntry.credit(1, 2, BigDecimal.TEN, "HKD").direction()).isEqualTo(EntryDirection.CREDIT);
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        assertThatThrownBy(() -> LedgerEntry.debit(1, 2, BigDecimal.ZERO, "HKD"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LedgerEntry.credit(1, 2, new BigDecimal("-1"), "HKD"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
