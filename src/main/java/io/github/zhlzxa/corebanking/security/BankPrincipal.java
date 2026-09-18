package io.github.zhlzxa.corebanking.security;

import io.github.zhlzxa.corebanking.user.UserRole;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * The authenticated caller, resolved from the token's external identity to the bank's own user.
 *
 * <p>Application code identifies the caller exclusively through {@link #userId()}. The external
 * issuer and subject are retained for audit purposes only.
 */
public record BankPrincipal(long userId, String issuer, String subject, UserRole role)
        implements AuthenticatedPrincipal {

    /** Returns the internal user id; the external subject is deliberately not used as a name. */
    @Override
    public String getName() {
        return Long.toString(userId);
    }
}
