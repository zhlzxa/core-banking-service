package io.github.zhlzxa.corebanking.audit;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds audit events. Keeping construction in one place guarantees that every event of a kind
 * records the same facts in the same way, and keeps sensitive values out of the trail.
 */
@Component
public class AuditEventFactory {

    static final String TRANSFER = "TRANSFER";
    static final String TRANSFER_REQUEST = "TRANSFER_REQUEST";
    static final String HTTP_ENDPOINT = "HTTP_ENDPOINT";
    static final String PAYEE = "PAYEE";

    private final Clock clock;

    public AuditEventFactory(Clock clock) {
        this.clock = clock;
    }

    /** A transfer took effect. The event references the transaction, which holds the instruction. */
    public AuditEvent transferCompleted(AuditContext context, String requestId, long transactionId) {
        return new AuditEvent(
                UUID.randomUUID(),
                clock.instant(),
                context.actor(),
                AuditAction.TRANSFER_COMPLETED,
                TRANSFER,
                Long.toString(transactionId),
                transactionId,
                requestId,
                context.correlationId(),
                context.channel(),
                AuditOutcome.SUCCESS,
                null,
                Map.of());
    }

    /**
     * A transfer attempt was rejected or failed. No transaction row survives such an attempt, so the
     * instruction itself (accounts, amount, currency) is recorded as metadata for investigations.
     */
    public AuditEvent transferUnsuccessful(
            AuditContext context,
            String requestId,
            AuditOutcome outcome,
            String reasonCode,
            Map<String, Object> instruction) {
        if (outcome == AuditOutcome.SUCCESS) {
            throw new IllegalArgumentException("Use transferCompleted for successful transfers");
        }
        return new AuditEvent(
                UUID.randomUUID(),
                clock.instant(),
                context.actor(),
                outcome == AuditOutcome.REJECTED ? AuditAction.TRANSFER_REJECTED : AuditAction.TRANSFER_FAILED,
                TRANSFER_REQUEST,
                requestId,
                null,
                requestId,
                context.correlationId(),
                context.channel(),
                outcome,
                reasonCode,
                instruction);
    }

    /**
     * A saved payee was added, renamed or removed. The account number is not recorded; the payee id
     * identifies the record, whose history is kept in this trail.
     */
    public AuditEvent payeeChanged(AuditContext context, AuditAction action, long payeeId) {
        return new AuditEvent(
                UUID.randomUUID(),
                clock.instant(),
                context.actor(),
                action,
                PAYEE,
                Long.toString(payeeId),
                null,
                null,
                context.correlationId(),
                context.channel(),
                AuditOutcome.SUCCESS,
                null,
                Map.of());
    }

    /** A request was stopped by authentication or authorization before any business logic ran. */
    public AuditEvent securityRejected(AuditContext context, AuditAction action, String path, String reasonCode) {
        return new AuditEvent(
                UUID.randomUUID(),
                clock.instant(),
                context.actor(),
                action,
                HTTP_ENDPOINT,
                path,
                null,
                null,
                context.correlationId(),
                context.channel(),
                AuditOutcome.REJECTED,
                reasonCode,
                Map.of());
    }
}
