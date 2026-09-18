package io.github.zhlzxa.corebanking.cash;

import io.github.zhlzxa.corebanking.account.Account;
import io.github.zhlzxa.corebanking.account.AccountNotFoundException;
import io.github.zhlzxa.corebanking.account.AccountRepository;
import io.github.zhlzxa.corebanking.audit.AuditAction;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.audit.AuditEventFactory;
import io.github.zhlzxa.corebanking.audit.AuditEventRepository;
import io.github.zhlzxa.corebanking.audit.MovementKind;
import io.github.zhlzxa.corebanking.posting.IdempotencyConflictException;
import io.github.zhlzxa.corebanking.security.Permissions;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cash handled by tellers at a branch counter.
 *
 * <p>A teller acts on behalf of the customer whose account is credited or debited. The teller is
 * recorded as actor and initiator, the customer as the person the action was for, and the branch
 * the teller signed in at as its location. A teller without an assigned branch cannot handle cash,
 * because the operation could not be attributed.
 *
 * <p>Withdrawals at or above {@link CashProperties#approvalThreshold()} follow the four-eyes
 * principle: the requesting teller (maker) only records the request, and a different teller
 * (checker) must approve it before the cash is paid out. Requests that are not decided within
 * {@link CashProperties#approvalValidity()} expire.
 */
@Service
@EnableConfigurationProperties(CashProperties.class)
public class TellerCashService {

    private final CashPostingService cashPostingService;
    private final WithdrawalApprovalRepository approvalRepository;
    private final AccountRepository accountRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventFactory auditEventFactory;
    private final CashProperties properties;
    private final Clock clock;

    public TellerCashService(
            CashPostingService cashPostingService,
            WithdrawalApprovalRepository approvalRepository,
            AccountRepository accountRepository,
            AuditEventRepository auditEventRepository,
            AuditEventFactory auditEventFactory,
            CashProperties properties,
            Clock clock) {
        this.cashPostingService = cashPostingService;
        this.approvalRepository = approvalRepository;
        this.accountRepository = accountRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventFactory = auditEventFactory;
        this.properties = properties;
        this.clock = clock;
    }

    /** Credits cash received at the counter to a customer account. */
    @Transactional
    @PreAuthorize(Permissions.TELLER_CASH)
    public BankTransaction deposit(AuditContext audit, CashCommand command) {
        requireBranch(audit);
        return cashPostingService.post(audit, MovementKind.CASH_DEPOSIT, command);
    }

    /**
     * Pays out cash from a customer account, or records a request for approval if the amount
     * reaches the approval threshold. Repeating a request with the same request id returns the
     * original outcome.
     */
    @Transactional
    @PreAuthorize(Permissions.TELLER_CASH)
    public WithdrawalResult withdraw(AuditContext audit, CashCommand command) {
        requireBranch(audit);
        if (command.amount().compareTo(properties.approvalThreshold()) < 0) {
            return new WithdrawalResult.Completed(
                    cashPostingService.post(audit, MovementKind.CASH_WITHDRAWAL, command));
        }
        return new WithdrawalResult.AwaitingApproval(requestApproval(audit, command));
    }

    /**
     * Approves a pending request and pays out the cash in the same transaction. The checker is the
     * actor of the withdrawal; the maker remains its initiator.
     *
     * <p>Approval exceptions do not roll back the transaction, so that an expiry detected here is
     * recorded even though the call fails with {@code APPROVAL_EXPIRED}.
     *
     * @throws ApprovalException if the request is unknown, already decided, expired, or the caller is
     *     its maker
     */
    @Transactional(noRollbackFor = ApprovalException.class)
    @PreAuthorize(Permissions.TELLER_CASH)
    public BankTransaction approve(AuditContext audit, long checkerUserId, long approvalId) {
        requireBranch(audit);
        WithdrawalApproval approval = lockPendingForDecision(audit, checkerUserId, approvalId);
        BankTransaction withdrawal = cashPostingService.post(audit, MovementKind.CASH_WITHDRAWAL, approval.toCommand());
        approvalRepository.markExecuted(approvalId, checkerUserId, withdrawal.id(), clock.instant());
        recordDecision(audit, AuditAction.WITHDRAWAL_APPROVAL_GRANTED, approval);
        return withdrawal;
    }

    /**
     * Declines a pending request. No money moves.
     *
     * @throws ApprovalException if the request is unknown, already decided, expired, or the caller is
     *     its maker
     */
    @Transactional(noRollbackFor = ApprovalException.class)
    @PreAuthorize(Permissions.TELLER_CASH)
    public WithdrawalApproval reject(AuditContext audit, long checkerUserId, long approvalId) {
        requireBranch(audit);
        WithdrawalApproval approval = lockPendingForDecision(audit, checkerUserId, approvalId);
        approvalRepository.markRejected(approvalId, checkerUserId, clock.instant());
        recordDecision(audit, AuditAction.WITHDRAWAL_APPROVAL_DECLINED, approval);
        return reload(approvalId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(Permissions.TELLER_CASH)
    public WithdrawalApproval getApproval(long approvalId) {
        return reload(approvalId);
    }

    private WithdrawalApproval requestApproval(AuditContext audit, CashCommand command) {
        cashPostingService.checkWithdrawable(audit, command);
        Instant expiresAt = clock.instant().plus(properties.approvalValidity());
        Optional<Long> created =
                approvalRepository.insertIfAbsent(command, audit.actor().branchCode(), expiresAt);
        if (created.isEmpty()) {
            WithdrawalApproval existing =
                    approvalRepository.findByRequestId(command.requestId()).orElseThrow();
            if (!existing.isSameRequestAs(command)) {
                throw new IdempotencyConflictException();
            }
            return existing;
        }
        WithdrawalApproval approval = reload(created.get());
        recordDecision(audit, AuditAction.WITHDRAWAL_APPROVAL_REQUESTED, approval);
        return approval;
    }

    private WithdrawalApproval lockPendingForDecision(AuditContext audit, long checkerUserId, long approvalId) {
        WithdrawalApproval approval =
                approvalRepository.findByIdForUpdate(approvalId).orElseThrow(ApprovalException::notFound);
        if (approval.status() != WithdrawalApproval.ApprovalStatus.PENDING) {
            throw ApprovalException.notPending();
        }
        if (approval.makerUserId() == checkerUserId) {
            throw ApprovalException.makerCannotDecide();
        }
        Instant now = clock.instant();
        if (approval.isExpiredAt(now)) {
            approvalRepository.markExpired(approvalId, now);
            recordDecision(audit, AuditAction.WITHDRAWAL_APPROVAL_EXPIRED, approval);
            throw ApprovalException.expired();
        }
        return approval;
    }

    private void recordDecision(AuditContext audit, AuditAction action, WithdrawalApproval approval) {
        Long customer = accountRepository
                .findById(approval.accountId())
                .map(Account::ownerUserId)
                .orElseThrow(AccountNotFoundException::new);
        auditEventRepository.append(auditEventFactory.approvalChanged(
                audit,
                action,
                approval.id(),
                customer,
                Map.of(
                        "requestId", approval.requestId(),
                        "makerUserId", approval.makerUserId(),
                        "amount", approval.amount().toPlainString(),
                        "currency", approval.currency())));
    }

    private WithdrawalApproval reload(long approvalId) {
        return approvalRepository.findById(approvalId).orElseThrow(ApprovalException::notFound);
    }

    private static void requireBranch(AuditContext audit) {
        if (audit.actor().branchCode() == null) {
            throw new AccessDeniedException("Teller is not signed in at a branch");
        }
    }
}
