package io.github.zhlzxa.corebanking.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AuditMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AuditMetrics metrics = new AuditMetrics(registry);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void anEventOutsideATransactionIsCountedImmediately() {
        metrics.count(event(AuditOutcome.REJECTED, "UNAUTHENTICATED"));

        assertThat(count("AUTHENTICATION_FAILED", "REJECTED", "UNAUTHENTICATED"))
                .isEqualTo(1);
    }

    @Test
    void anEventInsideATransactionIsCountedOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        metrics.count(event(AuditOutcome.SUCCESS, null));
        assertThat(count("AUTHENTICATION_FAILED", "SUCCESS", "none")).isZero();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(count("AUTHENTICATION_FAILED", "SUCCESS", "none")).isEqualTo(1);
    }

    @Test
    void anEventOfARolledBackTransactionIsNeverCounted() {
        TransactionSynchronizationManager.initSynchronization();

        metrics.count(event(AuditOutcome.SUCCESS, null));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(count("AUTHENTICATION_FAILED", "SUCCESS", "none")).isZero();
    }

    private double count(String action, String outcome, String reason) {
        var counter = registry.find(AuditMetrics.EVENTS)
                .tags("action", action, "outcome", outcome, "reason", reason)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private static AuditEvent event(AuditOutcome outcome, String reasonCode) {
        return new AuditEvent(
                UUID.randomUUID(),
                Instant.now(),
                AuditActor.anonymous(),
                null,
                AuditAction.AUTHENTICATION_FAILED,
                "HTTP_ENDPOINT",
                "/transfers",
                null,
                null,
                "corr-1",
                AuditChannel.API,
                outcome,
                reasonCode,
                Map.of());
    }
}
