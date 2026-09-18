package io.github.zhlzxa.corebanking.transaction;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/** A pagination cursor supplied by the client is malformed or was tampered with. */
public class InvalidCursorException extends BusinessException {

    public InvalidCursorException() {
        super(ErrorCode.INVALID_CURSOR, "The pagination cursor is invalid");
    }
}
