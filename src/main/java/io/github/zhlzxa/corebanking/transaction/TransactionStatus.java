package io.github.zhlzxa.corebanking.transaction;

/**
 * Lifecycle status of a transaction.
 *
 * <p>An internal transfer is created {@code PENDING} and moved to {@code COMPLETED} in the same
 * database transaction, so no other session ever observes the pending state. The distinction
 * becomes visible for payments that leave the bank and complete asynchronously.
 */
public enum TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED
}
