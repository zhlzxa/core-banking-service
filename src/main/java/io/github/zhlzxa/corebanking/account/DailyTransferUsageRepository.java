package io.github.zhlzxa.corebanking.account;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cumulative amount transferred out of an account per business day. */
public interface DailyTransferUsageRepository {

    /**
     * Adds {@code amount} to the day's usage if the new total stays within {@code limit}.
     *
     * <p>The check and the increment are a single atomic statement on the account's usage row, so
     * concurrent transfers cannot together exceed the limit even without any other lock. The
     * increment is part of the caller's transaction and is undone if the transfer rolls back.
     *
     * @return {@code true} if the amount was consumed, {@code false} if it would exceed the limit
     */
    boolean tryConsume(long accountId, LocalDate businessDay, BigDecimal amount, BigDecimal limit);
}
