package io.github.zhlzxa.corebanking.ledger;

/**
 * Side of a ledger posting, from the account holder's point of view: a debit decreases the account
 * balance, a credit increases it.
 */
public enum EntryDirection {
    DEBIT,
    CREDIT
}
