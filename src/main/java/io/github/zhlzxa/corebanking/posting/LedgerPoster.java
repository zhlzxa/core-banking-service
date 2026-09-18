package io.github.zhlzxa.corebanking.posting;

import io.github.zhlzxa.corebanking.account.AccountRepository;
import io.github.zhlzxa.corebanking.ledger.LedgerEntry;
import io.github.zhlzxa.corebanking.ledger.LedgerRepository;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.transaction.TransactionRepository;
import io.github.zhlzxa.corebanking.transaction.TransactionStatus;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Applies a balanced double-entry posting and completes the transaction.
 *
 * <p>Callers validate the business rules and hold the necessary row locks before posting; this
 * class only guarantees that both balances, both ledger legs and the status change are written
 * together within the caller's transaction.
 */
@Component
public class LedgerPoster {

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final TransactionRepository transactionRepository;

    public LedgerPoster(
            AccountRepository accountRepository,
            LedgerRepository ledgerRepository,
            TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Moves {@code amount} from the debit account to the credit account under the given
     * transaction and marks the transaction {@code COMPLETED}.
     *
     * @return the completed transaction
     */
    public BankTransaction post(
            long transactionId, long debitAccountId, long creditAccountId, BigDecimal amount, String currency) {
        return post(transactionId, debitAccountId, creditAccountId, amount, currency, TransactionStatus.COMPLETED);
    }

    /**
     * Posts like {@link #post(long, long, long, BigDecimal, String)} but leaves the transaction in
     * the given status, for movements whose final outcome is decided elsewhere, such as a payment
     * waiting for an external network.
     */
    public BankTransaction post(
            long transactionId,
            long debitAccountId,
            long creditAccountId,
            BigDecimal amount,
            String currency,
            TransactionStatus resultingStatus) {
        accountRepository.debit(debitAccountId, amount);
        accountRepository.credit(creditAccountId, amount);
        ledgerRepository.append(LedgerEntry.debit(transactionId, debitAccountId, amount, currency));
        ledgerRepository.append(LedgerEntry.credit(transactionId, creditAccountId, amount, currency));
        transactionRepository.updateStatus(transactionId, resultingStatus);
        return transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new IllegalStateException("Transaction " + transactionId + " vanished"));
    }
}
