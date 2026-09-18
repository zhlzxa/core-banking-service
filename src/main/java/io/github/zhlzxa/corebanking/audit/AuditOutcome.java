package io.github.zhlzxa.corebanking.audit;

/** Result of an audited action. */
public enum AuditOutcome {
    /** The action took effect. */
    SUCCESS,
    /** The action was refused by a business or security rule; nothing changed. */
    REJECTED,
    /** The action failed for a technical reason; nothing changed. */
    FAILED
}
