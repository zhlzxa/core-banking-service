package io.github.zhlzxa.corebanking.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcDailyTransferUsageRepository implements DailyTransferUsageRepository {

    private final JdbcClient jdbc;

    JdbcDailyTransferUsageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Both paths of the upsert are guarded. The {@code WHERE} on the {@code SELECT} rejects a first
     * transfer of the day that alone exceeds the limit; the {@code WHERE} on {@code DO UPDATE}
     * rejects an increment that would push an existing total over it. A rejected upsert returns no
     * row. A read-then-write ({@code SELECT sum} followed by an insert) would let two concurrent
     * transfers both pass the check.
     */
    @Override
    public boolean tryConsume(long accountId, LocalDate businessDay, BigDecimal amount, BigDecimal limit) {
        return jdbc.sql("""
                        INSERT INTO daily_transfer_usage (account_id, usage_date, used_amount)
                        SELECT :accountId, :businessDay, :amount
                        WHERE :amount <= :limit
                        ON CONFLICT (account_id, usage_date) DO UPDATE
                            SET used_amount = daily_transfer_usage.used_amount + EXCLUDED.used_amount,
                                updated_at = now()
                            WHERE daily_transfer_usage.used_amount + EXCLUDED.used_amount <= :limit
                        RETURNING used_amount
                        """)
                .param("accountId", accountId)
                .param("businessDay", businessDay)
                .param("amount", amount)
                .param("limit", limit)
                .query(BigDecimal.class)
                .optional()
                .isPresent();
    }

    @Override
    public void release(long accountId, LocalDate businessDay, BigDecimal amount) {
        jdbc.sql("""
                        UPDATE daily_transfer_usage
                        SET used_amount = GREATEST(used_amount - :amount, 0), updated_at = now()
                        WHERE account_id = :accountId AND usage_date = :businessDay
                        """)
                .param("accountId", accountId)
                .param("businessDay", businessDay)
                .param("amount", amount)
                .update();
    }
}
