package io.github.zhlzxa.corebanking.audit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Counts business outcomes as they are recorded in the audit trail, for example completed and
 * rejected transfers by reason, or failed authentications.
 *
 * <p>Every business outcome passes through the audit trail, so the counters cannot drift from what
 * was actually recorded. An event written inside a transaction is counted only after that
 * transaction commits. Tags are enum names and stable reason codes, never identifiers, so the number
 * of time series stays bounded.
 */
@Component
class AuditMetrics {

    static final String EVENTS = "corebanking.business.events";

    private final MeterRegistry registry;

    AuditMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void count(AuditEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    increment(event);
                }
            });
        } else {
            increment(event);
        }
    }

    private void increment(AuditEvent event) {
        Counter.builder(EVENTS)
                .description("Business outcomes recorded in the audit trail")
                .tag("action", event.action().name())
                .tag("outcome", event.outcome().name())
                .tag("reason", event.reasonCode() == null ? "none" : event.reasonCode())
                .register(registry)
                .increment();
    }
}
