package io.github.zhlzxa.corebanking.cash;

import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.audit.MovementKind;
import io.github.zhlzxa.corebanking.posting.AccountRuleViolationException;
import io.github.zhlzxa.corebanking.security.Permissions;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cash paid out by self-service terminals.
 *
 * <p>The card holder is authenticated by the card network at the terminal (card and PIN), which is
 * outside this service. This service trusts a registered, active terminal to send the account linked
 * to the verified card; what it enforces itself is the terminal's identity, a per-withdrawal cap,
 * and the same account rules and idempotency as any other withdrawal.
 */
@Service
@EnableConfigurationProperties(CashProperties.class)
public class AtmCashService {

    private final CashPostingService cashPostingService;
    private final CashProperties properties;

    public AtmCashService(CashPostingService cashPostingService, CashProperties properties) {
        this.cashPostingService = cashPostingService;
        this.properties = properties;
    }

    /**
     * @throws AccountRuleViolationException with {@code TRANSFER_LIMIT_EXCEEDED} if the amount exceeds
     *     the terminal withdrawal limit
     */
    @Transactional
    @PreAuthorize(Permissions.ATM_WITHDRAW)
    public BankTransaction withdraw(AuditContext audit, CashCommand command) {
        if (command.amount() != null && command.amount().compareTo(properties.atmWithdrawalLimit()) > 0) {
            throw AccountRuleViolationException.limitExceeded();
        }
        return cashPostingService.post(audit, MovementKind.CASH_WITHDRAWAL, command);
    }
}
