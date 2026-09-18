package io.github.zhlzxa.corebanking.transaction;

/** Business kind of a money movement recorded in the {@code transactions} table. */
public enum TransactionType {
    /** Between two customer accounts. */
    TRANSFER,
    /** Cash paid in: from the bank's cash account to a customer account. */
    DEPOSIT,
    /** Cash paid out: from a customer account to the bank's cash account. */
    WITHDRAWAL
}
