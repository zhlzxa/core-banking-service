package io.github.zhlzxa.corebanking.security;

import io.github.zhlzxa.corebanking.audit.AuditActor;
import io.github.zhlzxa.corebanking.audit.AuditChannel;

/**
 * An authenticated self-service terminal. A terminal has no bank user: it is recorded in the audit
 * trail by its terminal id and branch, and every action it performs is on behalf of the customer
 * whose account it operates on.
 */
public record TerminalPrincipal(String terminalId, String issuer, String subject, String branchCode)
        implements AuditablePrincipal {

    public static final String ROLE = "ATM";

    @Override
    public String getName() {
        return terminalId;
    }

    @Override
    public AuditActor toAuditActor() {
        return new AuditActor(null, issuer, subject, ROLE, terminalId, branchCode);
    }

    @Override
    public AuditChannel channel() {
        return AuditChannel.ATM;
    }
}
