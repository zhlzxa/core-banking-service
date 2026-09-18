package io.github.zhlzxa.corebanking.account;

import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One page of an account's transaction history.
 *
 * @param nextCursor opaque position to request the following page with, or {@code null} if this is
 *     the last page
 */
public record TransactionHistoryPage(
        long accountId,
        List<BankTransaction> transactions,
        @Nullable String nextCursor) {

    public boolean hasMore() {
        return nextCursor != null;
    }
}
