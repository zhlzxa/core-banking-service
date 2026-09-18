package io.github.zhlzxa.corebanking.transaction;

/**
 * Lifecycle status of a transaction.
 *
 * <p>A movement inside the bank is created {@code PENDING} and moved to {@code COMPLETED} in the
 * same database transaction, so no other session ever observes the pending state. A payment to
 * another bank is posted and then stays {@code PROCESSING} until the external system decides it.
 */
public enum TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,
    /** Posted locally; waiting for the external payment system to confirm or reject it. */
    PROCESSING,
    /** Rejected externally; a reversal transaction has returned the money. */
    REVERSED,
    /** The external outcome could not be determined automatically; a person must decide. */
    NEEDS_INVESTIGATION
}
