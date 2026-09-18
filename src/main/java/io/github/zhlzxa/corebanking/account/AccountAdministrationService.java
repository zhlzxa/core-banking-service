package io.github.zhlzxa.corebanking.account;

import io.github.zhlzxa.corebanking.audit.AuditAction;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.audit.AuditEventFactory;
import io.github.zhlzxa.corebanking.audit.AuditEventRepository;
import io.github.zhlzxa.corebanking.security.Permissions;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Back-office operations on accounts: freezing, unfreezing, closing and changing limits.
 *
 * <p>Each operation locks the account row, so it is serialised with transfers on the same account:
 * a transfer that has already locked the account completes first, and every transfer after the
 * change sees the new state. Each change is audited inside its transaction with the reason and the
 * previous values.
 */
@Service
public class AccountAdministrationService {

    private final AccountRepository accountRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventFactory auditEventFactory;

    public AccountAdministrationService(
            AccountRepository accountRepository,
            AuditEventRepository auditEventRepository,
            AuditEventFactory auditEventFactory) {
        this.accountRepository = accountRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventFactory = auditEventFactory;
    }

    /**
     * Blocks outgoing movements. Incoming payments continue to be accepted.
     *
     * @throws InvalidAccountStateException if the account is not active
     */
    @Transactional
    @PreAuthorize(Permissions.ADMIN_MANAGE_ACCOUNTS)
    public Account freeze(AuditContext audit, long accountId, StatusReason reason) {
        Objects.requireNonNull(reason, "reason");
        Account account = lock(accountId);
        requireStatus(account, AccountStatus.ACTIVE, "Only an active account can be frozen");
        accountRepository.updateStatus(accountId, AccountStatus.FROZEN, reason);
        record(audit, AuditAction.ACCOUNT_FROZEN, account, Map.of("reason", reason.name()));
        return reload(accountId);
    }

    /**
     * @throws InvalidAccountStateException if the account is not frozen
     */
    @Transactional
    @PreAuthorize(Permissions.ADMIN_MANAGE_ACCOUNTS)
    public Account unfreeze(AuditContext audit, long accountId) {
        Account account = lock(accountId);
        requireStatus(account, AccountStatus.FROZEN, "Only a frozen account can be unfrozen");
        accountRepository.updateStatus(accountId, AccountStatus.ACTIVE, null);
        record(audit, AuditAction.ACCOUNT_UNFROZEN, account, Map.of("previousReason", reasonOf(account)));
        return reload(accountId);
    }

    /**
     * Closes the account permanently. The balance must have been paid out first, so that closing
     * never makes money disappear from the ledger's point of view.
     *
     * @throws InvalidAccountStateException if the account is already closed or still holds money
     */
    @Transactional
    @PreAuthorize(Permissions.ADMIN_MANAGE_ACCOUNTS)
    public Account close(AuditContext audit, long accountId) {
        Account account = lock(accountId);
        if (!account.status().canTransitionTo(AccountStatus.CLOSED)) {
            throw new InvalidAccountStateException("The account is already closed");
        }
        if (account.balance().signum() != 0) {
            throw new InvalidAccountStateException("The balance must be zero before the account is closed");
        }
        accountRepository.updateStatus(accountId, AccountStatus.CLOSED, null);
        record(audit, AuditAction.ACCOUNT_CLOSED, account, Map.of());
        return reload(accountId);
    }

    /**
     * Replaces the transfer limits of a customer account; {@code null} removes a limit.
     *
     * @throws InvalidAccountStateException if the account is closed or bank-internal
     */
    @Transactional
    @PreAuthorize(Permissions.ADMIN_MANAGE_ACCOUNTS)
    public Account changeLimits(
            AuditContext audit,
            long accountId,
            @Nullable BigDecimal perTransactionLimit,
            @Nullable BigDecimal dailyLimit) {
        Account account = lock(accountId);
        if (!account.isCustomerAccount() || account.status() == AccountStatus.CLOSED) {
            throw new InvalidAccountStateException("Limits apply only to open customer accounts");
        }
        accountRepository.updateLimits(accountId, perTransactionLimit, dailyLimit);
        Map<String, Object> change = new HashMap<>();
        change.put("previousPerTransactionLimit", plain(account.perTransactionLimit()));
        change.put("previousDailyLimit", plain(account.dailyTransferLimit()));
        change.put("perTransactionLimit", plain(perTransactionLimit));
        change.put("dailyLimit", plain(dailyLimit));
        record(audit, AuditAction.TRANSFER_LIMITS_CHANGED, account, change);
        return reload(accountId);
    }

    private Account lock(long accountId) {
        return accountRepository.findByIdForUpdate(accountId).orElseThrow(AccountNotFoundException::new);
    }

    private Account reload(long accountId) {
        return accountRepository.findById(accountId).orElseThrow(AccountNotFoundException::new);
    }

    private static void requireStatus(Account account, AccountStatus expected, String message) {
        if (account.status() != expected) {
            throw new InvalidAccountStateException(message);
        }
    }

    private void record(AuditContext audit, AuditAction action, Account before, Map<String, Object> details) {
        Map<String, Object> metadata = new HashMap<>(details);
        metadata.put("previousStatus", before.status().name());
        auditEventRepository.append(auditEventFactory.accountChanged(audit, action, before.id(), metadata));
    }

    private static String reasonOf(Account account) {
        return account.statusReason() == null ? "" : account.statusReason().name();
    }

    /** Audit metadata cannot hold nulls; an absent limit is recorded as an empty string. */
    private static String plain(@Nullable BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
