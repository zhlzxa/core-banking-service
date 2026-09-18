package io.github.zhlzxa.corebanking.account;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle status of an account and the movements it permits.
 *
 * <p>Allowed transitions:
 *
 * <pre>
 *   ACTIVE  -> FROZEN, DORMANT, CLOSED
 *   FROZEN  -> ACTIVE, CLOSED
 *   DORMANT -> ACTIVE, CLOSED
 *   CLOSED  -> (terminal)
 * </pre>
 */
public enum AccountStatus {
    ACTIVE,
    /** Blocked for outgoing movements, typically by a court order or fraud investigation. */
    FROZEN,
    /** Unused for a long period; outgoing movements need reactivation first. */
    DORMANT,
    /** Terminal. */
    CLOSED;

    /** Whether money may leave the account. */
    public boolean canBeDebited() {
        return this == ACTIVE;
    }

    /**
     * Whether money may arrive. Frozen and dormant accounts still receive, so that salaries and
     * incoming payments are not bounced back to the payer.
     */
    public boolean canBeCredited() {
        return this != CLOSED;
    }

    public boolean canTransitionTo(AccountStatus target) {
        return allowedTargets().contains(target);
    }

    private Set<AccountStatus> allowedTargets() {
        return switch (this) {
            case ACTIVE -> EnumSet.of(FROZEN, DORMANT, CLOSED);
            case FROZEN, DORMANT -> EnumSet.of(ACTIVE, CLOSED);
            case CLOSED -> EnumSet.noneOf(AccountStatus.class);
        };
    }
}
