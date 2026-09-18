package io.github.zhlzxa.corebanking.transfer;

import io.github.zhlzxa.corebanking.account.Account;
import io.github.zhlzxa.corebanking.account.AccountNotFoundException;
import io.github.zhlzxa.corebanking.account.AccountRepository;
import io.github.zhlzxa.corebanking.account.DailyTransferUsageRepository;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.audit.AuditEventFactory;
import io.github.zhlzxa.corebanking.audit.AuditEventRepository;
import io.github.zhlzxa.corebanking.audit.AuditOutcome;
import io.github.zhlzxa.corebanking.audit.IndependentAuditRecorder;
import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import io.github.zhlzxa.corebanking.common.money.CurrencyUnits;
import io.github.zhlzxa.corebanking.common.time.BusinessCalendar;
import io.github.zhlzxa.corebanking.ledger.LedgerEntry;
import io.github.zhlzxa.corebanking.ledger.LedgerRepository;
import io.github.zhlzxa.corebanking.security.Permissions;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.transaction.NewTransaction;
import io.github.zhlzxa.corebanking.transaction.TransactionRepository;
import io.github.zhlzxa.corebanking.transaction.TransactionStatus;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes internal transfers on the double-entry ledger.
 *
 * <p>A transfer is a single database transaction that either applies completely or not at all:
 * both balance updates, both ledger legs, the transaction status change and the success audit
 * event commit together. Any exception rolls everything back, including the transaction row that
 * claimed the request id, so a rejected request can be retried with the same key.
 *
 * <p>Concurrency is controlled with pessimistic row locks. Both accounts are locked in ascending
 * id order regardless of transfer direction, so two opposite transfers between the same accounts
 * queue behind each other instead of deadlocking.
 *
 * <p>Authorization has three layers: the client application must hold the {@code bank.transfer}
 * scope, the caller must have the bank role {@code CUSTOMER}, and the source account must belong
 * to the caller. An account that exists but belongs to someone else is reported exactly like a
 * missing one, so that account identifiers cannot be probed.
 *
 * <p>Auditing follows the transaction boundary. A completed transfer is audited inside the
 * transfer transaction, so money never moves without an audit record. A rejected or failed attempt
 * is audited in an independent transaction, so the record survives the rollback. An idempotent
 * retry is not audited again.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerRepository ledgerRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventFactory auditEventFactory;
    private final IndependentAuditRecorder independentAuditRecorder;
    private final DailyTransferUsageRepository dailyTransferUsageRepository;
    private final BusinessCalendar businessCalendar;

    public TransferService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            LedgerRepository ledgerRepository,
            AuditEventRepository auditEventRepository,
            AuditEventFactory auditEventFactory,
            IndependentAuditRecorder independentAuditRecorder,
            DailyTransferUsageRepository dailyTransferUsageRepository,
            BusinessCalendar businessCalendar) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerRepository = ledgerRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventFactory = auditEventFactory;
        this.independentAuditRecorder = independentAuditRecorder;
        this.dailyTransferUsageRepository = dailyTransferUsageRepository;
        this.businessCalendar = businessCalendar;
    }

    /**
     * Transfers money between two accounts, or returns the original result if the request id was
     * already processed with the same instruction.
     *
     * @param audit who is acting, through which channel, under which correlation id
     * @return the completed transaction
     * @throws InvalidTransferException if the instruction is structurally invalid
     * @throws IdempotencyConflictException if the request id was used for another instruction
     * @throws AccountNotFoundException if either account does not exist, the source account is not
     *     owned by the customer, or the destination is not a customer account
     * @throws CurrencyMismatchException if the currency differs from either account
     * @throws InsufficientBalanceException if the source balance does not cover the amount
     * @throws AccountRuleViolationException if an account's status, the amount's precision or a
     *     transfer limit does not permit the transfer
     */
    @Transactional
    @PreAuthorize(Permissions.CUSTOMER_TRANSFER)
    public BankTransaction transfer(AuditContext audit, TransferCommand command) {
        try {
            return execute(audit, command);
        } catch (RuntimeException ex) {
            recordUnsuccessfulAttempt(audit, command, ex);
            throw ex;
        }
    }

    private BankTransaction execute(AuditContext audit, TransferCommand command) {
        validate(command);

        // Locks are taken before the request id is claimed: the transaction row references both
        // accounts, so their existence must be established first, and holding the locks early
        // costs nothing because a retry has to wait for the original request anyway.
        LockedAccounts accounts = lockInIdOrder(command.fromAccountId(), command.toAccountId());
        if (!accounts.source().isOwnedBy(command.customerId())
                || !accounts.destination().isCustomerAccount()) {
            throw new AccountNotFoundException();
        }

        NewTransaction instruction = command.toNewTransaction();
        Optional<Long> claimed = transactionRepository.insertPendingIfAbsent(instruction);
        if (claimed.isEmpty()) {
            return resolveRetry(instruction);
        }
        long transactionId = claimed.get();

        // Rules are evaluated on the locked rows, so no concurrent change can invalidate them before
        // commit. The order decides which reason a client sees when several rules fail: account
        // state first, then currency, then funds, then limits.
        Account source = accounts.source();
        Account destination = accounts.destination();
        if (!source.status().canBeDebited()) {
            throw AccountRuleViolationException.sourceNotActive();
        }
        if (!destination.status().canBeCredited()) {
            throw AccountRuleViolationException.destinationClosed();
        }
        if (!source.currency().equals(command.currency())
                || !destination.currency().equals(command.currency())) {
            throw new CurrencyMismatchException();
        }
        if (!source.hasSufficientBalanceFor(command.amount())) {
            throw new InsufficientBalanceException();
        }
        if (source.exceedsPerTransactionLimit(command.amount())) {
            throw AccountRuleViolationException.limitExceeded();
        }
        if (source.dailyTransferLimit() != null
                && !dailyTransferUsageRepository.tryConsume(
                        source.id(), businessCalendar.today(), command.amount(), source.dailyTransferLimit())) {
            throw AccountRuleViolationException.limitExceeded();
        }

        accountRepository.debit(source.id(), command.amount());
        accountRepository.credit(destination.id(), command.amount());
        ledgerRepository.append(LedgerEntry.debit(transactionId, source.id(), command.amount(), command.currency()));
        ledgerRepository.append(
                LedgerEntry.credit(transactionId, destination.id(), command.amount(), command.currency()));
        transactionRepository.updateStatus(transactionId, TransactionStatus.COMPLETED);
        auditEventRepository.append(auditEventFactory.transferCompleted(audit, command.requestId(), transactionId));

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
        if (!CurrencyUnits.isKnown(command.currency())) {
            throw new InvalidTransferException("Currency is not supported");
        }
        if (!CurrencyUnits.fitsMinorUnits(command.amount(), command.currency())) {
            throw AccountRuleViolationException.invalidAmountScale();
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

    /**
     * Records a rejected or failed attempt without affecting its outcome. The caller always sees the
     * original exception: if the audit write itself fails, that is logged for operations but must
     * not turn a business rejection into a technical error.
     */
    private void recordUnsuccessfulAttempt(AuditContext audit, TransferCommand command, RuntimeException cause) {
        AuditOutcome outcome;
        String reasonCode;
        if (cause instanceof BusinessException business) {
            outcome = AuditOutcome.REJECTED;
            reasonCode = business.errorCode().name();
        } else {
            outcome = AuditOutcome.FAILED;
            reasonCode = ErrorCode.INTERNAL_ERROR.name();
        }
        Map<String, Object> instruction = Map.of(
                "fromAccountId", command.fromAccountId(),
                "toAccountId", command.toAccountId(),
                "amount", command.amount() == null ? "" : command.amount().toPlainString(),
                "currency", String.valueOf(command.currency()));
        try {
            independentAuditRecorder.record(auditEventFactory.transferUnsuccessful(
                    audit, command.requestId(), outcome, reasonCode, instruction));
        } catch (RuntimeException auditFailure) {
            log.error(
                    "Failed to record audit event for unsuccessful transfer: reasonCode={}", reasonCode, auditFailure);
        }
    }

    private record LockedAccounts(Account source, Account destination) {}
}
