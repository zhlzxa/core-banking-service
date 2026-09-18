package io.github.zhlzxa.corebanking.audit;

/**
 * Business actions recorded in the audit trail. Names describe what happened in business terms, not
 * which method ran; they are stored as text and must never be renamed.
 */
public enum AuditAction {
    TRANSFER_COMPLETED,
    TRANSFER_REJECTED,
    TRANSFER_FAILED,
    CASH_DEPOSIT_COMPLETED,
    CASH_DEPOSIT_REJECTED,
    CASH_DEPOSIT_FAILED,
    CASH_WITHDRAWAL_COMPLETED,
    CASH_WITHDRAWAL_REJECTED,
    CASH_WITHDRAWAL_FAILED,
    WITHDRAWAL_APPROVAL_REQUESTED,
    WITHDRAWAL_APPROVAL_GRANTED,
    WITHDRAWAL_APPROVAL_DECLINED,
    WITHDRAWAL_APPROVAL_EXPIRED,
    ACCOUNT_FROZEN,
    ACCOUNT_UNFROZEN,
    ACCOUNT_CLOSED,
    TRANSFER_LIMITS_CHANGED,
    PAYEE_ADDED,
    PAYEE_RENAMED,
    PAYEE_REMOVED,
    AUTHENTICATION_FAILED,
    AUTHORIZATION_DENIED
}
