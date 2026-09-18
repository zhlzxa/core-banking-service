package io.github.zhlzxa.corebanking.transfer;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/** The transfer does not exist or does not involve any of the caller's accounts. */
public class TransferNotFoundException extends BusinessException {

    public TransferNotFoundException() {
        super(ErrorCode.TRANSFER_NOT_FOUND, "Transfer not found");
    }
}
