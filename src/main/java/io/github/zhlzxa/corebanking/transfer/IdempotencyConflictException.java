package io.github.zhlzxa.corebanking.transfer;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/**
 * The request id has already been used for a different instruction. Executing either instruction
 * silently would be wrong, so the request is rejected and the client must use a fresh key.
 */
public class IdempotencyConflictException extends BusinessException {

    public IdempotencyConflictException() {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSED, "The request id was already used for a different instruction");
    }
}
