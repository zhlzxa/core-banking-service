package io.github.zhlzxa.corebanking.account;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Snapshot of a ledger account.
 *
 * @param id internal account identifier
 * @param ownerUserId owning customer, or {@code null} for bank-internal accounts
 * @param currency ISO 4217 currency code; an account holds exactly one currency
 * @param balance current booked balance, kept consistent with the ledger in the same transaction
 */
public record Account(long id, @Nullable Long ownerUserId, String currency, BigDecimal balance) {

    public boolean isOwnedBy(long userId) {
        return ownerUserId != null && ownerUserId == userId;
    }

    public boolean isCustomerAccount() {
        return ownerUserId != null;
    }

    public boolean hasSufficientBalanceFor(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }
}
