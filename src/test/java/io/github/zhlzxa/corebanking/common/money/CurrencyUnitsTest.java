package io.github.zhlzxa.corebanking.common.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CurrencyUnitsTest {

    @Test
    void amountsMustFitTheCurrencyMinorUnits() {
        assertThat(CurrencyUnits.fitsMinorUnits(new BigDecimal("10.25"), "HKD")).isTrue();
        assertThat(CurrencyUnits.fitsMinorUnits(new BigDecimal("10.255"), "HKD"))
                .isFalse();
        assertThat(CurrencyUnits.fitsMinorUnits(new BigDecimal("500"), "JPY")).isTrue();
        assertThat(CurrencyUnits.fitsMinorUnits(new BigDecimal("500.5"), "JPY")).isFalse();
        assertThat(CurrencyUnits.fitsMinorUnits(new BigDecimal("1.125"), "KWD")).isTrue();
    }

    @Test
    void trailingZerosDoNotCountAsPrecision() {
        assertThat(CurrencyUnits.fitsMinorUnits(new BigDecimal("500.0000"), "JPY"))
                .isTrue();
        assertThat(CurrencyUnits.fitsMinorUnits(new BigDecimal("1E+3"), "JPY")).isTrue();
    }

    @Test
    void recognisesIsoCurrencies() {
        assertThat(CurrencyUnits.isKnown("HKD")).isTrue();
        assertThat(CurrencyUnits.isKnown("XYZ")).isFalse();
        assertThat(CurrencyUnits.isKnown(null)).isFalse();
    }
}
