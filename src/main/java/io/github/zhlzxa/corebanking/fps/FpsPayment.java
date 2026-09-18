package io.github.zhlzxa.corebanking.fps;

import io.github.zhlzxa.corebanking.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * An outgoing FPS payment: the local transaction together with its state at FPS.
 *
 * @param clearingAccountId the FPS clearing account the customer was debited against
 * @param attemptCount how many times reconciliation has looked at this payment
 */
public record FpsPayment(
        long id,
        String requestId,
        @Nullable Long initiatedByUserId,
        long fromAccountId,
        long clearingAccountId,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        ExternalStatus externalStatus,
        String endToEndId,
        String creditorBankCode,
        String creditorAccount,
        int attemptCount,
        @Nullable String lastErrorCode,
        Instant createdAt) {

    public boolean isUnresolved() {
        return status == TransactionStatus.PROCESSING;
    }

    public FpsInstruction toInstruction() {
        return new FpsInstruction(endToEndId, fromAccountId, creditorBankCode, creditorAccount, amount, currency);
    }

    /** Last known state of the payment at FPS. */
    public enum ExternalStatus {
        NOT_SENT,
        SENT,
        ACCEPTED,
        REJECTED,
        UNKNOWN
    }
}
