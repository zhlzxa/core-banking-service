package io.github.zhlzxa.corebanking.account;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/** The requested lifecycle change is not permitted from the account's current state. */
public class InvalidAccountStateException extends BusinessException {

    public InvalidAccountStateException(String message) {
        super(ErrorCode.INVALID_ACCOUNT_STATE, message);
    }
}
