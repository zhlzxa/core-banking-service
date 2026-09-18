package io.github.zhlzxa.corebanking.audit;

import org.jspecify.annotations.Nullable;

/**
 * Who performed an audited action, as established by authentication.
 *
 * <p>The internal user id is the primary reference. The external issuer and subject are stored as
 * well, so that the identity provider's records can be correlated even if a user record is later
 * changed. All fields are {@code null} for an unauthenticated caller.
 */
public record AuditActor(
        @Nullable Long userId,
        @Nullable String issuer,
        @Nullable String subject,
        @Nullable String role) {

    private static final AuditActor ANONYMOUS = new AuditActor(null, null, null, null);

    public static AuditActor anonymous() {
        return ANONYMOUS;
    }
}
