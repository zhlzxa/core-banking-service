package io.github.zhlzxa.corebanking.security;

import io.github.zhlzxa.corebanking.audit.AuditActor;
import io.github.zhlzxa.corebanking.audit.AuditChannel;
import io.github.zhlzxa.corebanking.user.UserRole;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * The authenticated caller, resolved from the token's external identity to the bank's own user.
 *
 * <p>Application code identifies the caller exclusively through {@link #userId()}. The external
 * issuer and subject are retained for audit purposes only.
 *
 * @param branchCode the branch a member of staff is signed in at, taken from the token's {@code
 *     branch_code} claim; {@code null} for customers
 */
public record BankPrincipal(
        long userId,
        String issuer,
        String subject,
        UserRole role,
        @Nullable String branchCode) implements AuthenticatedPrincipal {

    /** Returns the internal user id; the external subject is deliberately not used as a name. */
    @Override
    public String getName() {
        return Long.toString(userId);
    }

    public AuditActor toAuditActor() {
        return new AuditActor(userId, issuer, subject, role.name(), null, branchCode);
    }

    /**
     * The channel a request from this principal arrived through, derived from how the caller
     * authenticated rather than from anything the request claims about itself.
     */
    public AuditChannel channel() {
        return role == UserRole.TELLER ? AuditChannel.BRANCH : AuditChannel.API;
    }
}
