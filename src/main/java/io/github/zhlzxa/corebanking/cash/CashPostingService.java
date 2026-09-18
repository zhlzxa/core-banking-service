package io.github.zhlzxa.corebanking.cash;

import io.github.zhlzxa.corebanking.account.Account;
import io.github.zhlzxa.corebanking.account.AccountNotFoundException;
import io.github.zhlzxa.corebanking.account.AccountRepository;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.audit.AuditEventFactory;
import io.github.zhlzxa.corebanking.audit.AuditEventRepository;
import io.github.zhlzxa.corebanking.audit.BestEffortAuditRecorder;
import io.github.zhlzxa.corebanking.audit.MovementKind;
import io.github.zhlzxa.corebanking.common.money.CurrencyUnits;
import io.github.zhlzxa.corebanking.posting.AccountLocks;
import io.github.zhlzxa.corebanking.posting.AccountRuleViolationException;
import io.github.zhlzxa.corebanking.posting.CurrencyMismatchException;
import io.github.zhlzxa.corebanking.posting.IdempotentTransactions;
import io.github.zhlzxa.corebanking.posting.IdempotentTransactions.Claim;
import io.github.zhlzxa.corebanking.posting.InsufficientBalanceException;
import io.github.zhlzxa.corebanking.posting.LedgerPoster;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.transaction.NewTransaction;
import io.github.zhlzxa.corebanking.transaction.TransactionType;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Posts cash deposits and withdrawals between a customer account and the bank's cash account for
 * the currency ({@code CASH-<currency>}).
 *
 * <p>Must be called within a transaction; callers decide who may perform the operation. Only the
 * customer account is locked explicitly. The cash account's balance is changed by relative updates
 * only, whose row lock is always acquired after every customer account lock of the transaction, so
 * cash operations cannot form a lock cycle with transfers or with each other.
 */
@Component
class CashPostingService {

    private static final Logger log = LoggerFactory.getLogger(CashPostingService.class);

    private final AccountRepository accountRepository;
    private final AccountLocks accountLocks;
    private final IdempotentTransactions idempotentTransactions;
    private final LedgerPoster ledgerPoster;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventFactory auditEventFactory;
    private final BestEffortAuditRecorder bestEffortAuditRecorder;

    CashPostingService(
            AccountRepository accountRepository,
            AccountLocks accountLocks,
            IdempotentTransactions idempotentTransactions,
            LedgerPoster ledgerPoster,
            AuditEventRepository auditEventRepository,
            AuditEventFactory auditEventFactory,
            BestEffortAuditRecorder bestEffortAuditRecorder) {
        this.accountRepository = accountRepository;
        this.accountLocks = accountLocks;
        this.idempotentTransactions = idempotentTransactions;
        this.ledgerPoster = ledgerPoster;
        this.auditEventRepository = auditEventRepository;
        this.auditEventFactory = auditEventFactory;
        this.bestEffortAuditRecorder = bestEffortAuditRecorder;
    }

    /**
     * Executes the movement, or returns the original transaction for a repeated request id. Every
     * outcome is audited: success inside the transaction, rejection or failure independently.
     */
    BankTransaction post(AuditContext audit, MovementKind kind, CashCommand command) {
        try {
            return execute(audit, kind, command);
        } catch (RuntimeException ex) {
            bestEffortAuditRecorder.record(auditEventFactory.movementUnsuccessful(
                    audit, kind, command.requestId(), ex, instruction(command), null));
            throw ex;
        }
    }

    private BankTransaction execute(AuditContext audit, MovementKind kind, CashCommand command) {
        validate(command);
        long cashAccountId = cashAccountFor(command.currency());
        Account customer = accountLocks.lock(command.accountId());
        if (!customer.isCustomerAccount()) {
            throw new AccountNotFoundException();
        }

        boolean deposit = kind == MovementKind.CASH_DEPOSIT;
        long debitAccountId = deposit ? cashAccountId : customer.id();
        long creditAccountId = deposit ? customer.id() : cashAccountId;
        Claim claim = idempotentTransactions.claim(new NewTransaction(
                command.requestId(),
                command.operatorId(),
                deposit ? TransactionType.DEPOSIT : TransactionType.WITHDRAWAL,
                debitAccountId,
                creditAccountId,
                command.amount(),
                command.currency()));
        if (claim instanceof Claim.Retry retry) {
            return retry.original();
        }
        long transactionId = ((Claim.New) claim).transactionId();

        if (deposit && !customer.status().canBeCredited()) {
            throw AccountRuleViolationException.destinationClosed();
        }
        if (!deposit && !customer.status().canBeDebited()) {
            throw AccountRuleViolationException.sourceNotActive();
        }
        if (!customer.currency().equals(command.currency())) {
            throw new CurrencyMismatchException();
        }
        if (!deposit && !customer.hasSufficientBalanceFor(command.amount())) {
            throw new InsufficientBalanceException();
        }

        BankTransaction completed =
                ledgerPoster.post(transactionId, debitAccountId, creditAccountId, command.amount(), command.currency());
        auditEventRepository.append(auditEventFactory.movementCompleted(
                audit, kind, command.requestId(), transactionId, customer.ownerUserId()));
        log.info("Cash movement completed: kind={}, transactionId={}", kind, transactionId);
        return completed;
    }

    private static void validate(CashCommand command) {
        if (command.amount() == null || command.amount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (!CurrencyUnits.isKnown(command.currency())) {
            throw new CurrencyNotSupportedException();
        }
        if (!CurrencyUnits.fitsMinorUnits(command.amount(), command.currency())) {
            throw AccountRuleViolationException.invalidAmountScale();
        }
    }

    private long cashAccountFor(String currency) {
        try {
            return accountRepository.findInternalAccountId("CASH-" + currency);
        } catch (AccountNotFoundException e) {
            throw new CurrencyNotSupportedException();
        }
    }

    private static Map<String, Object> instruction(CashCommand command) {
        return Map.of(
                "accountId", command.accountId(),
                "amount", command.amount() == null ? "" : command.amount().toPlainString(),
                "currency", String.valueOf(command.currency()));
    }
}
