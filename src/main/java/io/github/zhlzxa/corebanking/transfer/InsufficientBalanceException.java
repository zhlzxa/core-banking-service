package io.github.zhlzxa.corebanking.transfer;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/** The source account balance does not cover the requested amount. */
public class InsufficientBalanceException extends BusinessException {

    public InsufficientBalanceException() {
        super(ErrorCode.INSUFFICIENT_BALANCE, "The source account has insufficient balance");
    }
}
