package io.github.zhlzxa.corebanking.payee;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/** The payee does not exist or belongs to another customer. */
public class PayeeNotFoundException extends BusinessException {

    public PayeeNotFoundException() {
        super(ErrorCode.PAYEE_NOT_FOUND, "Payee not found");
    }
}
