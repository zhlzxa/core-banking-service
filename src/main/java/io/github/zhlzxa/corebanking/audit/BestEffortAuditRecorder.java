package io.github.zhlzxa.corebanking.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Records events whose loss must not change the outcome of the request: rejected and failed
 * attempts, and security rejections. The event is written in its own transaction; if that fails,
 * the failure is logged at {@code ERROR} for alerting and the caller continues. See ADR-0004.
 */
@Component
public class BestEffortAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(BestEffortAuditRecorder.class);

    private final IndependentAuditRecorder independentAuditRecorder;

    public BestEffortAuditRecorder(IndependentAuditRecorder independentAuditRecorder) {
        this.independentAuditRecorder = independentAuditRecorder;
    }

    public void record(AuditEvent event) {
        try {
            independentAuditRecorder.record(event);
        } catch (RuntimeException ex) {
            log.error("Failed to record audit event: action={}, reasonCode={}", event.action(), event.reasonCode(), ex);
        }
    }
}
