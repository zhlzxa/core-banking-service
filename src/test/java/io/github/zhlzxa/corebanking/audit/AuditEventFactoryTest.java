package io.github.zhlzxa.corebanking.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zhlzxa.corebanking.transfer.InsufficientBalanceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventFactoryTest {

    private static final Instant NOW = Instant.parse("2026-09-18T08:00:00Z");
    private static final AuditContext CUSTOMER =
            new AuditContext(new AuditActor(7L, "https://issuer", "alice", "CUSTOMER"), "corr-1", AuditChannel.API);
    private static final AuditContext TELLER = new AuditContext(
            new AuditActor(3L, "https://issuer", "teller", "TELLER", null, "BR-001"), "corr-2", AuditChannel.BRANCH);

    private final AuditEventFactory factory = new AuditEventFactory(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void completedMovementReferencesTheTransaction() {
        AuditEvent event = factory.movementCompleted(CUSTOMER, MovementKind.TRANSFER, "req-1", 42, 7L);

        assertThat(event.action()).isEqualTo(AuditAction.TRANSFER_COMPLETED);
        assertThat(event.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.transactionId()).isEqualTo(42L);
        assertThat(event.resourceId()).isEqualTo("42");
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.onBehalfOfUserId()).isEqualTo(7L);
        assertThat(event.correlationId()).isEqualTo("corr-1");
        assertThat(event.reasonCode()).isNull();
    }

    @Test
    void staffActingForACustomerRecordsBoth() {
        AuditEvent event = factory.movementCompleted(TELLER, MovementKind.CASH_DEPOSIT, "req-2", 43, 7L);

        assertThat(event.action()).isEqualTo(AuditAction.CASH_DEPOSIT_COMPLETED);
        assertThat(event.actor().userId()).isEqualTo(3L);
        assertThat(event.actor().branchCode()).isEqualTo("BR-001");
        assertThat(event.onBehalfOfUserId()).isEqualTo(7L);
        assertThat(event.channel()).isEqualTo(AuditChannel.BRANCH);
    }

    @Test
    void businessRejectionIsRejectedWithItsErrorCode() {
        AuditEvent event = factory.movementUnsuccessful(
                CUSTOMER,
                MovementKind.TRANSFER,
                "req-1",
                new InsufficientBalanceException(),
                Map.of("amount", "10.00"),
                7L);

        assertThat(event.action()).isEqualTo(AuditAction.TRANSFER_REJECTED);
        assertThat(event.outcome()).isEqualTo(AuditOutcome.REJECTED);
        assertThat(event.reasonCode()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(event.transactionId()).isNull();
        assertThat(event.metadata()).containsEntry("amount", "10.00");
    }

    @Test
    void unexpectedFailureIsFailedWithoutLeakingTheExceptionMessage() {
        AuditEvent event = factory.movementUnsuccessful(
                CUSTOMER,
                MovementKind.CASH_WITHDRAWAL,
                "req-1",
                new IllegalStateException("connection to db-host-7 refused"),
                Map.of(),
                7L);

        assertThat(event.action()).isEqualTo(AuditAction.CASH_WITHDRAWAL_FAILED);
        assertThat(event.reasonCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(event.toString()).doesNotContain("db-host-7");
    }

    @Test
    void payeeChangesAreOnBehalfOfTheActingCustomer() {
        assertThat(factory.payeeChanged(CUSTOMER, AuditAction.PAYEE_ADDED, 5).onBehalfOfUserId())
                .isEqualTo(7L);
    }

    @Test
    void nonSuccessfulEventsRequireAReason() {
        assertThatThrownBy(() -> new AuditEvent(
                        UUID.randomUUID(),
                        NOW,
                        AuditActor.anonymous(),
                        null,
                        AuditAction.TRANSFER_REJECTED,
                        "MOVEMENT_REQUEST",
                        null,
                        null,
                        null,
                        null,
                        AuditChannel.API,
                        AuditOutcome.REJECTED,
                        null,
                        Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void securityRejectionRecordsTheEndpoint() {
        AuditEvent event = factory.securityRejected(
                new AuditContext(AuditActor.anonymous(), "corr-3", AuditChannel.API),
                AuditAction.AUTHENTICATION_FAILED,
                "/transfers",
                "UNAUTHENTICATED");

        assertThat(event.resourceType()).isEqualTo("HTTP_ENDPOINT");
        assertThat(event.resourceId()).isEqualTo("/transfers");
        assertThat(event.actor().userId()).isNull();
        assertThat(event.outcome()).isEqualTo(AuditOutcome.REJECTED);
    }
}
