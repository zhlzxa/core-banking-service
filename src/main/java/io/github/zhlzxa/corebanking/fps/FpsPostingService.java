package io.github.zhlzxa.corebanking.fps;

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
import io.github.zhlzxa.corebanking.posting.CurrencyNotSupportedException;
import io.github.zhlzxa.corebanking.posting.IdempotencyConflictException;
import io.github.zhlzxa.corebanking.posting.IdempotentTransactions;
import io.github.zhlzxa.corebanking.posting.IdempotentTransactions.Claim;
import io.github.zhlzxa.corebanking.posting.InsufficientBalanceException;
import io.github.zhlzxa.corebanking.posting.LedgerPoster;
import io.github.zhlzxa.corebanking.posting.OutgoingLimits;
import io.github.zhlzxa.corebanking.transaction.NewTransaction;
import io.github.zhlzxa.corebanking.transaction.TransactionStatus;
import io.github.zhlzxa.corebanking.transaction.TransactionType;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * First phase of an FPS payment: validates the instruction and debits the customer, in one local
 * transaction that commits before FPS is contacted.
 *
 * <p>The customer is debited against the FPS clearing account for the currency, so the money is no
 * longer available to spend while its fate is unknown. The payment is left {@code PROCESSING} with
 * a fresh end-to-end id, and is made due for reconciliation after a grace period in case the process
 * stops before the payment is sent.
 */
@Service
class FpsPostingService {

    private final AccountRepository accountRepository;
    private final AccountLocks accountLocks;
    private final IdempotentTransactions idempotentTransactions;
    private final LedgerPoster ledgerPoster;
    private final OutgoingLimits outgoingLimits;
    private final FpsPaymentRepository paymentRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventFactory auditEventFactory;
    private final BestEffortAuditRecorder bestEffortAuditRecorder;
    private final FpsProperties properties;
    private final Clock clock;

    FpsPostingService(
            AccountRepository accountRepository,
            AccountLocks accountLocks,
            IdempotentTransactions idempotentTransactions,
            LedgerPoster ledgerPoster,
            OutgoingLimits outgoingLimits,
            FpsPaymentRepository paymentRepository,
            AuditEventRepository auditEventRepository,
            AuditEventFactory auditEventFactory,
            BestEffortAuditRecorder bestEffortAuditRecorder,
            FpsProperties properties,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.accountLocks = accountLocks;
        this.idempotentTransactions = idempotentTransactions;
        this.ledgerPoster = ledgerPoster;
        this.outgoingLimits = outgoingLimits;
        this.paymentRepository = paymentRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventFactory = auditEventFactory;
        this.bestEffortAuditRecorder = bestEffortAuditRecorder;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Posts a new payment, or returns the existing one for a repeated request id.
     *
     * @throws IdempotencyConflictException if the request id was used for a different payment
     */
    @Transactional
    public FpsPayment initiate(AuditContext audit, FpsPaymentCommand command) {
        try {
            return execute(audit, command);
        } catch (RuntimeException ex) {
            bestEffortAuditRecorder.record(auditEventFactory.movementUnsuccessful(
                    audit,
                    MovementKind.FPS_PAYMENT,
                    command.requestId(),
                    ex,
                    instruction(command),
                    command.customerId()));
            throw ex;
        }
    }

    private FpsPayment execute(AuditContext audit, FpsPaymentCommand command) {
        validate(command);
        long clearingAccountId = clearingAccountFor(command.currency());
        Account source = accountLocks.lock(command.fromAccountId());
        if (!source.isOwnedBy(command.customerId())) {
            throw new AccountNotFoundException();
        }

        Claim claim = idempotentTransactions.claim(new NewTransaction(
                command.requestId(),
                command.customerId(),
                TransactionType.FPS_PAYMENT,
                source.id(),
                clearingAccountId,
                command.amount(),
                command.currency()));
        if (claim instanceof Claim.Retry retry) {
            FpsPayment existing =
                    paymentRepository.findById(retry.original().id()).orElseThrow();
            if (!existing.creditorBankCode().equals(command.creditorBankCode())
                    || !existing.creditorAccount().equals(command.creditorAccount())) {
                throw new IdempotencyConflictException();
            }
            return existing;
        }
        long transactionId = ((Claim.New) claim).transactionId();

        if (!source.status().canBeDebited()) {
            throw AccountRuleViolationException.sourceNotActive();
        }
        if (!source.currency().equals(command.currency())) {
            throw new CurrencyMismatchException();
        }
        if (!source.hasSufficientBalanceFor(command.amount())) {
            throw new InsufficientBalanceException();
        }
        outgoingLimits.consume(source, command.amount());

        paymentRepository.attachPaymentDetails(
                transactionId,
                newEndToEndId(),
                command.creditorBankCode(),
                command.creditorAccount(),
                clock.instant().plus(properties.submissionGrace()));
        ledgerPoster.post(
                transactionId,
                source.id(),
                clearingAccountId,
                command.amount(),
                command.currency(),
                TransactionStatus.PROCESSING);
        auditEventRepository.append(auditEventFactory.movementCompleted(
                audit, MovementKind.FPS_PAYMENT, command.requestId(), transactionId, command.customerId()));
        return paymentRepository.findById(transactionId).orElseThrow();
    }

    private static void validate(FpsPaymentCommand command) {
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

    private long clearingAccountFor(String currency) {
        try {
            return accountRepository.findInternalAccountId("FPS-CLEARING-" + currency);
        } catch (AccountNotFoundException e) {
            throw new CurrencyNotSupportedException();
        }
    }

    /** 35 characters, the maximum of the ISO 20022 end-to-end identification. */
    private static String newEndToEndId() {
        return "E2E" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private static Map<String, Object> instruction(FpsPaymentCommand command) {
        return Map.of(
                "fromAccountId", command.fromAccountId(),
                "creditorBankCode", String.valueOf(command.creditorBankCode()),
                "amount", command.amount() == null ? "" : command.amount().toPlainString(),
                "currency", String.valueOf(command.currency()));
    }
}
