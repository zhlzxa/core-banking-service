package io.github.zhlzxa.corebanking.ledger;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One leg of a double-entry posting. Every transaction produces legs whose debits and credits sum
 * to the same amount.
 */
public record LedgerEntry(
        long transactionId, long accountId, EntryDirection direction, BigDecimal amount, String currency) {

    public LedgerEntry {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Ledger amounts must be positive");
        }
    }

    public static LedgerEntry debit(long transactionId, long accountId, BigDecimal amount, String currency) {
        return new LedgerEntry(transactionId, accountId, EntryDirection.DEBIT, amount, currency);
    }

    public static LedgerEntry credit(long transactionId, long accountId, BigDecimal amount, String currency) {
        return new LedgerEntry(transactionId, accountId, EntryDirection.CREDIT, amount, currency);
    }
}
