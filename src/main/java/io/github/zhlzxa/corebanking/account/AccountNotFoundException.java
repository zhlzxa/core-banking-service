package io.github.zhlzxa.corebanking.account;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/**
 * The account does not exist or is not visible to the caller. Both cases are reported identically
 * so that callers cannot probe which account identifiers exist.
 */
public class AccountNotFoundException extends BusinessException {

    public AccountNotFoundException() {
        super(ErrorCode.ACCOUNT_NOT_FOUND, "Account not found");
    }
}
