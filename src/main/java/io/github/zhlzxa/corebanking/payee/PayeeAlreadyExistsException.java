package io.github.zhlzxa.corebanking.payee;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/** The customer has already saved this destination account. */
public class PayeeAlreadyExistsException extends BusinessException {

    public PayeeAlreadyExistsException() {
        super(ErrorCode.PAYEE_ALREADY_EXISTS, "This destination is already saved as a payee");
    }
}
