package io.github.zhlzxa.corebanking.posting;

import io.github.zhlzxa.corebanking.posting.IdempotentTransactions.Claim;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.transaction.NewTransaction;
import io.github.zhlzxa.corebanking.transaction.TransactionType;
import org.springframework.stereotype.Component;

/**
 * Undoes a posted movement with a new, offsetting transaction.
 *
 * <p>Ledger entries are never changed or deleted. A reversal is a transaction of its own that moves
 * the same amount back, so both the original movement and its correction remain visible. The
 * reversal's request id is derived from the original transaction, which makes reversing twice
 * impossible: a second attempt resolves to the first reversal.
 */
@Component
public class Reversals {

    private final IdempotentTransactions idempotentTransactions;
    private final LedgerPoster ledgerPoster;

    public Reversals(IdempotentTransactions idempotentTransactions, LedgerPoster ledgerPoster) {
        this.idempotentTransactions = idempotentTransactions;
        this.ledgerPoster = ledgerPoster;
    }

    /**
     * Posts the reversal of {@code original} within the caller's transaction.
     *
     * @return the reversal transaction, new or previously posted
     */
    public BankTransaction reverse(BankTransaction original) {
        Claim claim = idempotentTransactions.claim(new NewTransaction(
                "REVERSAL-" + original.id(),
                null,
                TransactionType.REVERSAL,
                original.toAccountId(),
                original.fromAccountId(),
                original.amount(),
                original.currency()));
        if (claim instanceof Claim.Retry retry) {
            return retry.original();
        }
        long reversalId = ((Claim.New) claim).transactionId();
        return ledgerPoster.post(
                reversalId, original.toAccountId(), original.fromAccountId(), original.amount(), original.currency());
    }
}
