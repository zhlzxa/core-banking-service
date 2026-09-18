package io.github.zhlzxa.corebanking.account;

import java.math.BigDecimal;

/**
 * Snapshot of a ledger account.
 *
 * @param id internal account identifier
 * @param currency ISO 4217 currency code; an account holds exactly one currency
 * @param balance current booked balance, kept consistent with the ledger in the same transaction
 */
public record Account(long id, String currency, BigDecimal balance) {

    public boolean hasSufficientBalanceFor(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }
}
