package io.github.zhlzxa.corebanking.transaction;

import java.math.BigDecimal;

/** Values required to register a new money movement. */
public record NewTransaction(
        String requestId,
        TransactionType type,
        long fromAccountId,
        long toAccountId,
        BigDecimal amount,
        String currency) {}
