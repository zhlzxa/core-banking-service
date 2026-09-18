package io.github.zhlzxa.corebanking.audit;

import org.jspecify.annotations.Nullable;

/**
 * Who performed an audited action, as established by authentication.
 *
 * <p>The internal user id is the primary reference. The external issuer and subject are stored as
 * well, so that the identity provider's records can be correlated even if a user record is later
 * changed. For a machine such as an ATM there is no user; the terminal id identifies it. All fields
 * are {@code null} for an unauthenticated caller.
 *
 * @param terminalId the terminal the action came from, for machine actors
 * @param branchCode the branch a member of staff was working at
 */
public record AuditActor(
        @Nullable Long userId,
        @Nullable String issuer,
        @Nullable String subject,
        @Nullable String role,
        @Nullable String terminalId,
        @Nullable String branchCode) {

    private static final AuditActor ANONYMOUS = new AuditActor(null, null, null, null);
    private static final AuditActor SYSTEM = new AuditActor(null, null, "core-banking-service", "SYSTEM");

    /** An actor without terminal or branch, such as a customer using the public API. */
    public AuditActor(@Nullable Long userId, @Nullable String issuer, @Nullable String subject, @Nullable String role) {
        this(userId, issuer, subject, role, null, null);
    }

    public static AuditActor anonymous() {
        return ANONYMOUS;
    }

    /** The service itself, acting in an automated job rather than on a request. */
    public static AuditActor system() {
        return SYSTEM;
    }
}
