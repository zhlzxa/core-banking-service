package io.github.zhlzxa.corebanking.audit;

/** Kinds of money movement and the audit actions recorded for their outcomes. */
public enum MovementKind {
    TRANSFER(AuditAction.TRANSFER_COMPLETED, AuditAction.TRANSFER_REJECTED, AuditAction.TRANSFER_FAILED),
    CASH_DEPOSIT(
            AuditAction.CASH_DEPOSIT_COMPLETED, AuditAction.CASH_DEPOSIT_REJECTED, AuditAction.CASH_DEPOSIT_FAILED),
    CASH_WITHDRAWAL(
            AuditAction.CASH_WITHDRAWAL_COMPLETED,
            AuditAction.CASH_WITHDRAWAL_REJECTED,
            AuditAction.CASH_WITHDRAWAL_FAILED);

    private final AuditAction completed;
    private final AuditAction rejected;
    private final AuditAction failed;

    MovementKind(AuditAction completed, AuditAction rejected, AuditAction failed) {
        this.completed = completed;
        this.rejected = rejected;
        this.failed = failed;
    }

    public AuditAction completed() {
        return completed;
    }

    public AuditAction rejected() {
        return rejected;
    }

    public AuditAction failed() {
        return failed;
    }
}
