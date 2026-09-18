package io.github.zhlzxa.corebanking.posting;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/**
 * The instruction currency differs from the currency of one of the accounts. Cross-currency
 * transfers require foreign exchange and are rejected rather than converted implicitly.
 */
public class CurrencyMismatchException extends BusinessException {

    public CurrencyMismatchException() {
        super(ErrorCode.CURRENCY_MISMATCH, "The transfer currency does not match the account currency");
    }
}
