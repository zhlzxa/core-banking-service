package io.github.zhlzxa.corebanking.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records an audit event in its own, independent database transaction.
 *
 * <p>Used for rejected and failed attempts: the business transaction that detected the problem is
 * about to roll back, and an event appended inside it would be rolled back too. This must be a
 * separate bean so that calls go through the transactional proxy; a {@code REQUIRES_NEW} method
 * invoked on {@code this} would silently join the caller's transaction.
 *
 * <p>While the new transaction runs, the caller's transaction and its connection stay open, so the
 * connection pool must provide at least two connections per concurrently audited request.
 */
@Service
public class IndependentAuditRecorder {

    private final AuditEventRepository auditEventRepository;

    public IndependentAuditRecorder(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        auditEventRepository.append(event);
    }
}
