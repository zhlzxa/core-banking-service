package io.github.zhlzxa.corebanking.transfer;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/**
 * A transfer is not permitted by the state or configuration of one of its accounts: status, amount
 * precision or limits. The error code identifies which rule was violated.
 */
public class AccountRuleViolationException extends BusinessException {

    private AccountRuleViolationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static AccountRuleViolationException sourceNotActive() {
        return new AccountRuleViolationException(
                ErrorCode.SOURCE_ACCOUNT_NOT_ACTIVE, "The source account cannot send money in its current status");
    }

    public static AccountRuleViolationException destinationClosed() {
        return new AccountRuleViolationException(
                ErrorCode.DESTINATION_ACCOUNT_CLOSED, "The destination account is closed");
    }

    public static AccountRuleViolationException invalidAmountScale() {
        return new AccountRuleViolationException(
                ErrorCode.INVALID_AMOUNT_SCALE, "The amount has more decimal places than the currency allows");
    }

    public static AccountRuleViolationException limitExceeded() {
        return new AccountRuleViolationException(
                ErrorCode.TRANSFER_LIMIT_EXCEEDED, "The transfer exceeds a limit of the source account");
    }
}
