package io.github.zhlzxa.corebanking.audit;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Builds audit events. Keeping construction in one place guarantees that every event of a kind
 * records the same facts in the same way, and keeps sensitive values out of the trail.
 */
@Component
public class AuditEventFactory {

    static final String TRANSACTION = "TRANSACTION";
    static final String MOVEMENT_REQUEST = "MOVEMENT_REQUEST";
    static final String HTTP_ENDPOINT = "HTTP_ENDPOINT";
    static final String PAYEE = "PAYEE";
    static final String ACCOUNT = "ACCOUNT";
    static final String WITHDRAWAL_APPROVAL = "WITHDRAWAL_APPROVAL";

    private final Clock clock;

    public AuditEventFactory(Clock clock) {
        this.clock = clock;
    }

    /**
     * A money movement took effect. The event references the transaction, which holds the
     * instruction.
     *
     * @param onBehalfOfUserId the customer whose account was debited or credited
     */
    public AuditEvent movementCompleted(
            AuditContext context,
            MovementKind kind,
            String requestId,
            long transactionId,
            @Nullable Long onBehalfOfUserId) {
        return event(
                context,
                onBehalfOfUserId,
                kind.completed(),
                TRANSACTION,
                Long.toString(transactionId),
                transactionId,
                requestId,
                AuditOutcome.SUCCESS,
                null,
                Map.of());
    }

    /**
     * A money movement was rejected or failed. Business rule violations are {@code REJECTED} with
     * their error code; anything else is {@code FAILED} with {@code INTERNAL_ERROR}. No transaction
     * row survives such an attempt, so the instruction itself is recorded as metadata.
     */
    public AuditEvent movementUnsuccessful(
            AuditContext context,
            MovementKind kind,
            @Nullable String requestId,
            RuntimeException cause,
            Map<String, Object> instruction,
            @Nullable Long onBehalfOfUserId) {
        boolean rejected = cause instanceof BusinessException;
        String reasonCode = cause instanceof BusinessException business
                ? business.errorCode().name()
                : ErrorCode.INTERNAL_ERROR.name();
        return event(
                context,
                onBehalfOfUserId,
                rejected ? kind.rejected() : kind.failed(),
                MOVEMENT_REQUEST,
                requestId,
                null,
                requestId,
                rejected ? AuditOutcome.REJECTED : AuditOutcome.FAILED,
                reasonCode,
                instruction);
    }

    /**
     * A saved payee was added, renamed or removed. The account number is not recorded; the payee id
     * identifies the record, whose history is kept in this trail.
     */
    public AuditEvent payeeChanged(AuditContext context, AuditAction action, long payeeId) {
        return event(
                context,
                context.actor().userId(),
                action,
                PAYEE,
                Long.toString(payeeId),
                null,
                null,
                AuditOutcome.SUCCESS,
                null,
                Map.of());
    }

    /**
     * A back-office change to an account's status or limits.
     *
     * @param details reason and previous values; the actor is the member of staff who acted
     */
    public AuditEvent accountChanged(
            AuditContext context, AuditAction action, long accountId, Map<String, Object> details) {
        return event(
                context,
                null,
                action,
                ACCOUNT,
                Long.toString(accountId),
                null,
                null,
                AuditOutcome.SUCCESS,
                null,
                details);
    }

    /**
     * A step in the four-eyes approval of a withdrawal: requested by the maker, granted or declined by
     * the checker, or expired.
     *
     * @param onBehalfOfUserId the customer whose account the withdrawal is for
     */
    public AuditEvent approvalChanged(
            AuditContext context,
            AuditAction action,
            long approvalId,
            @Nullable Long onBehalfOfUserId,
            Map<String, Object> details) {
        return event(
                context,
                onBehalfOfUserId,
                action,
                WITHDRAWAL_APPROVAL,
                Long.toString(approvalId),
                null,
                null,
                AuditOutcome.SUCCESS,
                null,
                details);
    }

    /**
     * The outcome of a payment to another bank became known, or its resolution was escalated.
     *
     * @param onBehalfOfUserId the customer who made the payment
     */
    public AuditEvent paymentOutcome(
            AuditContext context,
            AuditAction action,
            long transactionId,
            @Nullable Long onBehalfOfUserId,
            Map<String, Object> details) {
        return event(
                context,
                onBehalfOfUserId,
                action,
                TRANSACTION,
                Long.toString(transactionId),
                transactionId,
                null,
                AuditOutcome.SUCCESS,
                null,
                details);
    }

    /** A request was stopped by authentication or authorization before any business logic ran. */
    public AuditEvent securityRejected(AuditContext context, AuditAction action, String path, String reasonCode) {
        return event(
                context, null, action, HTTP_ENDPOINT, path, null, null, AuditOutcome.REJECTED, reasonCode, Map.of());
    }

    private AuditEvent event(
            AuditContext context,
            @Nullable Long onBehalfOfUserId,
            AuditAction action,
            String resourceType,
            @Nullable String resourceId,
            @Nullable Long transactionId,
            @Nullable String requestId,
            AuditOutcome outcome,
            @Nullable String reasonCode,
            Map<String, Object> metadata) {
        return new AuditEvent(
                UUID.randomUUID(),
                clock.instant(),
                context.actor(),
                onBehalfOfUserId,
                action,
                resourceType,
                resourceId,
                transactionId,
                requestId,
                context.correlationId(),
                context.channel(),
                outcome,
                reasonCode,
                metadata);
    }
}
