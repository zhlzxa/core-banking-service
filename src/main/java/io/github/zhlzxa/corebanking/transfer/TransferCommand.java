package io.github.zhlzxa.corebanking.transfer;

import io.github.zhlzxa.corebanking.transaction.NewTransaction;
import io.github.zhlzxa.corebanking.transaction.TransactionType;
import java.math.BigDecimal;

/**
 * Instruction to move money between two accounts held at this bank.
 *
 * @param requestId client-generated idempotency key; retries must reuse it
 * @param fromAccountId account to debit
 * @param toAccountId account to credit
 * @param amount positive amount in {@code currency}
 * @param currency ISO 4217 code; must match both accounts
 */
public record TransferCommand(
        String requestId, long fromAccountId, long toAccountId, BigDecimal amount, String currency) {

    NewTransaction toNewTransaction() {
        return new NewTransaction(requestId, TransactionType.TRANSFER, fromAccountId, toAccountId, amount, currency);
    }
}
