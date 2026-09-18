package io.github.zhlzxa.corebanking.cash;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;

/** A withdrawal approval cannot be decided as requested. The error code states why. */
public class ApprovalException extends BusinessException {

    private ApprovalException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ApprovalException notFound() {
        return new ApprovalException(ErrorCode.APPROVAL_NOT_FOUND, "Approval not found");
    }

    public static ApprovalException notPending() {
        return new ApprovalException(ErrorCode.APPROVAL_NOT_PENDING, "The approval has already been decided");
    }

    public static ApprovalException expired() {
        return new ApprovalException(ErrorCode.APPROVAL_EXPIRED, "The approval request has expired");
    }

    public static ApprovalException makerCannotDecide() {
        return new ApprovalException(
                ErrorCode.FOUR_EYES_REQUIRED,
                "A request must be decided by a different teller than the one who made it");
    }
}
