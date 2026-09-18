package io.github.zhlzxa.corebanking.transfer;

import io.github.zhlzxa.corebanking.account.Account;
import io.github.zhlzxa.corebanking.account.AccountNotFoundException;
import io.github.zhlzxa.corebanking.account.AccountRepository;
import io.github.zhlzxa.corebanking.ledger.LedgerEntry;
import io.github.zhlzxa.corebanking.ledger.LedgerRepository;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.transaction.NewTransaction;
import io.github.zhlzxa.corebanking.transaction.TransactionRepository;
import io.github.zhlzxa.corebanking.transaction.TransactionStatus;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes internal transfers on the double-entry ledger.
 *
 * <p>A transfer is a single database transaction that either applies completely or not at all:
 * both balance updates, both ledger legs and the transaction status change commit together. Any
 * exception rolls everything back, including the transaction row that claimed the request id, so
 * a rejected request can be retried with the same key.
 *
 * <p>Concurrency is controlled with pessimistic row locks. Both accounts are locked in ascending
 * id order regardless of transfer direction, so two opposite transfers between the same accounts
 * queue behind each other instead of deadlocking.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerRepository ledgerRepository;

    public TransferService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            LedgerRepository ledgerRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerRepository = ledgerRepository;
    }

    /**
     * Transfers money between two accounts, or returns the original result if the request id was
     * already processed with the same instruction.
     *
     * @return the completed transaction
     * @throws InvalidTransferException if the instruction is structurally invalid
     * @throws IdempotencyConflictException if the request id was used for another instruction
     * @throws AccountNotFoundException if either account does not exist
     * @throws CurrencyMismatchException if the currency differs from either account
     * @throws InsufficientBalanceException if the source balance does not cover the amount
     */
    @Transactional
    public BankTransaction transfer(TransferCommand command) {
        validate(command);

        NewTransaction instruction = command.toNewTransaction();
        Optional<Long> claimed = transactionRepository.insertPendingIfAbsent(instruction);
        if (claimed.isEmpty()) {
            return resolveRetry(instruction);
        }
        long transactionId = claimed.get();

        LockedAccounts accounts = lockInIdOrder(command.fromAccountId(), command.toAccountId());
        Account source = accounts.source();
        Account destination = accounts.destination();

        if (!source.currency().equals(command.currency())
                || !destination.currency().equals(command.currency())) {
            throw new CurrencyMismatchException();
        }
        if (!source.hasSufficientBalanceFor(command.amount())) {
            throw new InsufficientBalanceException();
        }

        accountRepository.debit(source.id(), command.amount());
        accountRepository.credit(destination.id(), command.amount());
        ledgerRepository.append(LedgerEntry.debit(transactionId, source.id(), command.amount(), command.currency()));
        ledgerRepository.append(
                LedgerEntry.credit(transactionId, destination.id(), command.amount(), command.currency()));
        transactionRepository.updateStatus(transactionId, TransactionStatus.COMPLETED);

        log.info("Transfer completed: transactionId={}", transactionId);
        return transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new IllegalStateException("Transaction " + transactionId + " vanished"));
    }

    private static void validate(TransferCommand command) {
        if (command.fromAccountId() == command.toAccountId()) {
            throw new InvalidTransferException("Source and destination accounts must differ");
        }
        if (command.amount() == null || command.amount().signum() <= 0) {
            throw new InvalidTransferException("Amount must be positive");
        }
    }

    /**
     * The request id is already taken. Because the claiming insert waits for any in-flight
     * transaction with the same key, the row found here is always committed.
     */
    private BankTransaction resolveRetry(NewTransaction instruction) {
        BankTransaction existing = transactionRepository
                .findByRequestId(instruction.requestId())
                .orElseThrow(() -> new IllegalStateException("Request id claimed but no transaction found"));
        if (!existing.isSameInstructionAs(instruction)) {
            throw new IdempotencyConflictException();
        }
        log.info("Idempotent retry resolved: transactionId={}", existing.id());
        return existing;
    }

    private LockedAccounts lockInIdOrder(long fromAccountId, long toAccountId) {
        long firstId = Math.min(fromAccountId, toAccountId);
        long secondId = Math.max(fromAccountId, toAccountId);
        Account first = accountRepository.findByIdForUpdate(firstId).orElseThrow(AccountNotFoundException::new);
        Account second = accountRepository.findByIdForUpdate(secondId).orElseThrow(AccountNotFoundException::new);
        return first.id() == fromAccountId ? new LockedAccounts(first, second) : new LockedAccounts(second, first);
    }

    private record LockedAccounts(Account source, Account destination) {}
}
