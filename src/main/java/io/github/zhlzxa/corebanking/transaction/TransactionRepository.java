package io.github.zhlzxa.corebanking.transaction;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Persistence operations on money movements. */
public interface TransactionRepository {

    /**
     * Registers a new transaction in {@code PENDING} status unless one with the same request id
     * already exists.
     *
     * <p>If a concurrent transaction is inserting the same request id, this call blocks until that
     * transaction commits or rolls back, so at most one caller can ever claim a request id.
     *
     * @return the id of the new transaction, or empty if the request id was already taken
     */
    Optional<Long> insertPendingIfAbsent(NewTransaction transaction);

    Optional<BankTransaction> findById(long transactionId);

    Optional<BankTransaction> findByRequestId(String requestId);

    /**
     * Transactions in which the account is either the source or the destination, newest first,
     * starting strictly after {@code after}.
     *
     * @param after position of the last row already returned, or {@code null} for the first page
     * @param limit maximum number of rows to return
     */
    List<BankTransaction> findHistory(long accountId, @Nullable HistoryCursor after, int limit);

    /**
     * @throws IllegalStateException if the transaction does not exist
     */
    void updateStatus(long transactionId, TransactionStatus status);
}
