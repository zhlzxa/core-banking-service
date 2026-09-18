package io.github.zhlzxa.corebanking.transaction;

import java.util.Optional;

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
     * @throws IllegalStateException if the transaction does not exist
     */
    void updateStatus(long transactionId, TransactionStatus status);
}
