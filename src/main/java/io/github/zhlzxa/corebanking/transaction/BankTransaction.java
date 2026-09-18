package io.github.zhlzxa.corebanking.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/** A recorded money movement between two accounts. */
public record BankTransaction(
        long id,
        String requestId,
        TransactionType type,
        TransactionStatus status,
        long fromAccountId,
        long toAccountId,
        BigDecimal amount,
        String currency,
        Instant createdAt) {

    /**
     * Whether this transaction was created from the same instruction as {@code candidate}.
     *
     * <p>Used to decide whether a repeated request id is a legitimate retry or a client error.
     * Amounts are compared numerically, so {@code 100.0} and {@code 100.00} are the same amount.
     */
    public boolean isSameInstructionAs(NewTransaction candidate) {
        return type == candidate.type()
                && fromAccountId == candidate.fromAccountId()
                && toAccountId == candidate.toAccountId()
                && amount.compareTo(candidate.amount()) == 0
                && currency.equals(candidate.currency());
    }
}
