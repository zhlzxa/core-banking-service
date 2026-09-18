package io.github.zhlzxa.corebanking.user;

/** Whether a user may currently use the bank's APIs. */
public enum UserStatus {
    ACTIVE,
    /** Temporarily blocked, for example after suspected fraud. */
    LOCKED,
    /** Permanently deactivated. */
    DISABLED;

    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
