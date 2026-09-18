package io.github.zhlzxa.corebanking.cash;

import io.github.zhlzxa.corebanking.transaction.BankTransaction;

/** Outcome of a teller withdrawal request. */
public sealed interface WithdrawalResult {

    /** The cash was paid out immediately. */
    record Completed(BankTransaction transaction) implements WithdrawalResult {}

    /** The amount requires a second teller's approval; no money has moved yet. */
    record AwaitingApproval(WithdrawalApproval approval) implements WithdrawalResult {}
}
