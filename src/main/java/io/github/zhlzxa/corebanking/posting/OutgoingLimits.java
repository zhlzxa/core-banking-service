package io.github.zhlzxa.corebanking.posting;

import io.github.zhlzxa.corebanking.account.Account;
import io.github.zhlzxa.corebanking.account.DailyTransferUsageRepository;
import io.github.zhlzxa.corebanking.common.time.BusinessCalendar;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Enforces the per-transaction and daily limits on money a customer sends out of an account, by
 * transfer or by payment to another bank. See ADR-0007.
 */
@Component
public class OutgoingLimits {

    private final DailyTransferUsageRepository dailyTransferUsageRepository;
    private final BusinessCalendar businessCalendar;

    public OutgoingLimits(
            DailyTransferUsageRepository dailyTransferUsageRepository, BusinessCalendar businessCalendar) {
        this.dailyTransferUsageRepository = dailyTransferUsageRepository;
        this.businessCalendar = businessCalendar;
    }

    /**
     * Checks the per-transaction limit and consumes the amount from today's limit. Must run inside
     * the movement's transaction so that a rollback also gives the consumed amount back.
     *
     * @throws AccountRuleViolationException with {@code TRANSFER_LIMIT_EXCEEDED} if a limit is exceeded
     */
    public void consume(Account source, BigDecimal amount) {
        if (source.exceedsPerTransactionLimit(amount)) {
            throw AccountRuleViolationException.limitExceeded();
        }
        if (source.dailyTransferLimit() != null
                && !dailyTransferUsageRepository.tryConsume(
                        source.id(), businessCalendar.today(), amount, source.dailyTransferLimit())) {
            throw AccountRuleViolationException.limitExceeded();
        }
    }
}
