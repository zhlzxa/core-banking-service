package io.github.zhlzxa.corebanking.account;

import java.math.BigDecimal;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Snapshot of a ledger account.
 *
 * @param id internal account identifier
 * @param ownerUserId owning customer, or {@code null} for bank-internal accounts
 * @param currency ISO 4217 currency code; an account holds exactly one currency
 * @param balance current booked balance, kept consistent with the ledger in the same transaction
 * @param status lifecycle status, deciding which movements are permitted
 * @param statusReason why the account is frozen; {@code null} otherwise
 * @param perTransactionLimit maximum amount of a single outgoing transfer, or {@code null} if none
 * @param dailyTransferLimit maximum total of outgoing transfers per business day, or {@code null}
 */
public record Account(
        long id,
        @Nullable Long ownerUserId,
        String currency,
        BigDecimal balance,
        AccountStatus status,
        @Nullable StatusReason statusReason,
        @Nullable BigDecimal perTransactionLimit,
        @Nullable BigDecimal dailyTransferLimit) {

    public Account {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(status, "status");
    }

    public boolean isOwnedBy(long userId) {
        return ownerUserId != null && ownerUserId == userId;
    }

    public boolean isCustomerAccount() {
        return ownerUserId != null;
    }

    public boolean hasSufficientBalanceFor(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public boolean exceedsPerTransactionLimit(BigDecimal amount) {
        return perTransactionLimit != null && amount.compareTo(perTransactionLimit) > 0;
    }
}
