package io.github.zhlzxa.corebanking.cash;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A large cash withdrawal waiting for, or having received, a second teller's decision.
 *
 * @param makerUserId the teller who requested the withdrawal
 * @param checkerUserId the teller who approved or rejected it; never the maker
 * @param transactionId the withdrawal transaction once executed
 */
public record WithdrawalApproval(
        long id,
        String requestId,
        long accountId,
        BigDecimal amount,
        String currency,
        long makerUserId,
        String makerBranchCode,
        ApprovalStatus status,
        @Nullable Long checkerUserId,
        @Nullable Long transactionId,
        Instant createdAt,
        Instant expiresAt) {

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isSameRequestAs(CashCommand command) {
        return makerUserId == command.operatorId()
                && accountId == command.accountId()
                && amount.compareTo(command.amount()) == 0
                && currency.equals(command.currency());
    }

    public CashCommand toCommand() {
        return new CashCommand(makerUserId, requestId, accountId, amount, currency);
    }

    public enum ApprovalStatus {
        PENDING,
        EXECUTED,
        REJECTED,
        EXPIRED
    }
}
