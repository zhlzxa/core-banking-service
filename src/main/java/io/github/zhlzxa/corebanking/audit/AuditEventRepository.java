package io.github.zhlzxa.corebanking.audit;

/**
 * Append-only access to the audit trail. There is deliberately no update or delete operation, and
 * the database rejects both as well.
 *
 * <p>{@link #append} joins the caller's transaction: an event appended during a business
 * transaction is committed or rolled back together with it.
 */
public interface AuditEventRepository {

    void append(AuditEvent event);
}
