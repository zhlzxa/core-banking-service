package io.github.zhlzxa.corebanking.cash;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/** The bank does not handle cash in this currency. */
public class CurrencyNotSupportedException extends BusinessException {

    public CurrencyNotSupportedException() {
        super(ErrorCode.CURRENCY_NOT_SUPPORTED, "Cash is not handled in this currency");
    }
}
