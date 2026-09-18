package io.github.zhlzxa.corebanking.account.web;

import io.github.zhlzxa.corebanking.account.Account;
import io.github.zhlzxa.corebanking.common.money.MoneyFormatter;

/** An account as seen by its owner. The balance is a string to preserve its exact decimal value. */
public record AccountResponse(long accountId, String currency, String balance, String status) {

    static AccountResponse from(Account account) {
        return new AccountResponse(
                account.id(),
                account.currency(),
                MoneyFormatter.format(account.balance(), account.currency()),
                account.status().name());
    }
}
