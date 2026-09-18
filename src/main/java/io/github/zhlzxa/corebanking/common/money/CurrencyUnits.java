package io.github.zhlzxa.corebanking.common.money;

import java.math.BigDecimal;
import java.util.Currency;

/** ISO 4217 facts about currencies needed to validate amounts. */
public final class CurrencyUnits {

    private CurrencyUnits() {}

    public static boolean isKnown(String currencyCode) {
        try {
            Currency.getInstance(currencyCode);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    /**
     * Whether the amount can be represented in the currency's minor units, for example at most two
     * decimal places for HKD and none for JPY. Trailing zeros are ignored, so {@code 100.00} JPY is
     * accepted as 100 yen.
     *
     * @throws IllegalArgumentException if the currency code is not a known ISO 4217 code
     */
    public static boolean fitsMinorUnits(BigDecimal amount, String currencyCode) {
        int minorUnits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        return amount.stripTrailingZeros().scale() <= minorUnits;
    }
}
