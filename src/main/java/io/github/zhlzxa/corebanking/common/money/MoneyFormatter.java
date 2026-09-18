package io.github.zhlzxa.corebanking.common.money;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Renders monetary amounts for API responses.
 *
 * <p>Amounts are transported as strings so that no client parses them into binary floating point.
 * Values are shown with at least the currency's standard number of minor units ({@code 100.00}
 * for HKD, {@code 100} for JPY) and never lose precision.
 */
public final class MoneyFormatter {

    private MoneyFormatter() {}

    public static String format(BigDecimal amount, String currencyCode) {
        int minorUnits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        int significantScale = Math.max(0, amount.stripTrailingZeros().scale());
        return amount.setScale(Math.max(minorUnits, significantScale)).toPlainString();
    }
}
