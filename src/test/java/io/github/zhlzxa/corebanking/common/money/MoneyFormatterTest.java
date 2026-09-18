package io.github.zhlzxa.corebanking.common.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyFormatterTest {

    @Test
    void usesTheCurrencyMinorUnits() {
        assertThat(MoneyFormatter.format(new BigDecimal("100.0000"), "HKD")).isEqualTo("100.00");
        assertThat(MoneyFormatter.format(new BigDecimal("100"), "USD")).isEqualTo("100.00");
        assertThat(MoneyFormatter.format(new BigDecimal("500.0000"), "JPY")).isEqualTo("500");
        assertThat(MoneyFormatter.format(new BigDecimal("1.5"), "KWD")).isEqualTo("1.500");
    }

    @Test
    void neverDropsSignificantDigits() {
        assertThat(MoneyFormatter.format(new BigDecimal("10.1250"), "HKD")).isEqualTo("10.125");
    }

    @Test
    void neverUsesScientificNotation() {
        assertThat(MoneyFormatter.format(new BigDecimal("1E+3"), "HKD")).isEqualTo("1000.00");
    }
}
