package io.github.zhlzxa.corebanking.fps;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/** The payment does not exist or was not sent from one of the caller's accounts. */
public class FpsPaymentNotFoundException extends BusinessException {

    public FpsPaymentNotFoundException() {
        super(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found");
    }
}
