package io.github.zhlzxa.corebanking.transfer;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/** The transfer instruction is structurally invalid, for example it targets its own source account. */
public class InvalidTransferException extends BusinessException {

    public InvalidTransferException(String message) {
        super(ErrorCode.INVALID_TRANSFER, message);
    }
}
