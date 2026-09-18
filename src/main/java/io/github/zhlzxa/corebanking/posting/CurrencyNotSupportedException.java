package io.github.zhlzxa.corebanking.posting;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/** The bank does not handle the requested kind of movement in this currency. */
public class CurrencyNotSupportedException extends BusinessException {

    public CurrencyNotSupportedException() {
        super(ErrorCode.CURRENCY_NOT_SUPPORTED, "This currency is not supported for the requested operation");
    }
}
