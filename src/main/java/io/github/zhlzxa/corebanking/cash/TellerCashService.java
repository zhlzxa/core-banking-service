package io.github.zhlzxa.corebanking.cash;

import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.audit.MovementKind;
import io.github.zhlzxa.corebanking.security.Permissions;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
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
 */
@Service
public class TellerCashService {

    private final CashPostingService cashPostingService;

    public TellerCashService(CashPostingService cashPostingService) {
        this.cashPostingService = cashPostingService;
    }

    /** Credits cash received at the counter to a customer account. */
    @Transactional
    @PreAuthorize(Permissions.TELLER_CASH)
    public BankTransaction deposit(AuditContext audit, CashCommand command) {
        requireBranch(audit);
        return cashPostingService.post(audit, MovementKind.CASH_DEPOSIT, command);
    }

    /** Debits a customer account for cash paid out at the counter. */
    @Transactional
    @PreAuthorize(Permissions.TELLER_CASH)
    public BankTransaction withdraw(AuditContext audit, CashCommand command) {
        requireBranch(audit);
        return cashPostingService.post(audit, MovementKind.CASH_WITHDRAWAL, command);
    }

    private static void requireBranch(AuditContext audit) {
        if (audit.actor().branchCode() == null) {
            throw new AccessDeniedException("Teller is not signed in at a branch");
        }
    }
}
