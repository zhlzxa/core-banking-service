package io.github.zhlzxa.corebanking.transaction;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Values required to register a new money movement.
 *
 * @param initiatedByUserId user on whose behalf the movement is requested, or {@code null} for
 *     movements initiated by the bank itself
 */
public record NewTransaction(
        String requestId,
        @Nullable Long initiatedByUserId,
        TransactionType type,
        long fromAccountId,
        long toAccountId,
        BigDecimal amount,
        String currency) {}
