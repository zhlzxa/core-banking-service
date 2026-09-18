package io.github.zhlzxa.corebanking.posting;

import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.transaction.NewTransaction;
import io.github.zhlzxa.corebanking.transaction.TransactionRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Claims client request ids for money movements. See ADR-0002.
 *
 * <p>A request id is claimed by inserting the transaction in {@code PENDING} status. If the id is
 * already taken, the stored transaction is compared with the new instruction: the same instruction
 * is a retry and yields the original transaction; a different one is a client error.
 */
@Component
public class IdempotentTransactions {

    private final TransactionRepository transactionRepository;

    public IdempotentTransactions(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * @throws IdempotencyConflictException if the request id was used for a different instruction
     */
    public Claim claim(NewTransaction instruction) {
        Optional<Long> claimed = transactionRepository.insertPendingIfAbsent(instruction);
        if (claimed.isPresent()) {
            return new Claim.New(claimed.get());
        }
        // The claiming insert waits for any in-flight transaction with the same key, so the row
        // found here is always committed.
        BankTransaction existing = transactionRepository
                .findByRequestId(instruction.requestId())
                .orElseThrow(() -> new IllegalStateException("Request id claimed but no transaction found"));
        if (!existing.isSameInstructionAs(instruction)) {
            throw new IdempotencyConflictException();
        }
        return new Claim.Retry(existing);
    }

    /** Outcome of claiming a request id. */
    public sealed interface Claim {

        /** The request id was free; the caller must now execute the movement. */
        record New(long transactionId) implements Claim {}

        /** The request was processed before; the caller must return this result unchanged. */
        record Retry(BankTransaction original) implements Claim {}
    }
}
