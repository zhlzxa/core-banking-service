package io.github.zhlzxa.corebanking.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditEventFactoryTest {

    private static final Instant NOW = Instant.parse("2026-09-18T08:00:00Z");
    private static final AuditContext CONTEXT =
            new AuditContext(new AuditActor(7L, "https://issuer", "alice", "CUSTOMER"), "corr-1", AuditChannel.API);

    private final AuditEventFactory factory = new AuditEventFactory(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void completedTransferReferencesTheTransaction() {
        AuditEvent event = factory.transferCompleted(CONTEXT, "req-1", 42);

        assertThat(event.action()).isEqualTo(AuditAction.TRANSFER_COMPLETED);
        assertThat(event.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.transactionId()).isEqualTo(42L);
        assertThat(event.resourceId()).isEqualTo("42");
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.actor().userId()).isEqualTo(7L);
        assertThat(event.correlationId()).isEqualTo("corr-1");
        assertThat(event.reasonCode()).isNull();
    }

    @Test
    void rejectedAndFailedAttemptsUseDistinctActionsAndHaveNoTransaction() {
        AuditEvent rejected = factory.transferUnsuccessful(
                CONTEXT, "req-1", AuditOutcome.REJECTED, "INSUFFICIENT_BALANCE", Map.of("amount", "10.00"));
        AuditEvent failed =
                factory.transferUnsuccessful(CONTEXT, "req-2", AuditOutcome.FAILED, "INTERNAL_ERROR", Map.of());

        assertThat(rejected.action()).isEqualTo(AuditAction.TRANSFER_REJECTED);
        assertThat(rejected.transactionId()).isNull();
        assertThat(rejected.resourceId()).isEqualTo("req-1");
        assertThat(rejected.metadata()).containsEntry("amount", "10.00");
        assertThat(failed.action()).isEqualTo(AuditAction.TRANSFER_FAILED);
    }

    @Test
    void successIsNotAnUnsuccessfulOutcome() {
        assertThatThrownBy(() -> factory.transferUnsuccessful(CONTEXT, "req", AuditOutcome.SUCCESS, "X", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonSuccessfulEventsRequireAReason() {
        assertThatThrownBy(() -> factory.transferUnsuccessful(CONTEXT, "req", AuditOutcome.REJECTED, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void securityRejectionRecordsTheEndpoint() {
        AuditEvent event = factory.securityRejected(
                new AuditContext(AuditActor.anonymous(), "corr-2", AuditChannel.API),
                AuditAction.AUTHENTICATION_FAILED,
                "/transfers",
                "UNAUTHENTICATED");

        assertThat(event.resourceType()).isEqualTo("HTTP_ENDPOINT");
        assertThat(event.resourceId()).isEqualTo("/transfers");
        assertThat(event.actor().userId()).isNull();
        assertThat(event.outcome()).isEqualTo(AuditOutcome.REJECTED);
    }
}
