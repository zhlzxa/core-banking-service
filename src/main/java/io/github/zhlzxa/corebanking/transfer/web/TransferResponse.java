package io.github.zhlzxa.corebanking.transfer.web;

import io.github.zhlzxa.corebanking.common.money.MoneyFormatter;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import java.time.Instant;

/** Response body describing a transfer. Amounts are strings to preserve exact decimal values. */
public record TransferResponse(
        long transactionId,
        String requestId,
        String status,
        long fromAccountId,
        long toAccountId,
        String amount,
        String currency,
        Instant createdAt) {

    static TransferResponse from(BankTransaction transaction) {
        return new TransferResponse(
                transaction.id(),
                transaction.requestId(),
                transaction.status().name(),
                transaction.fromAccountId(),
                transaction.toAccountId(),
                MoneyFormatter.format(transaction.amount(), transaction.currency()),
                transaction.currency(),
                transaction.createdAt());
    }
}
