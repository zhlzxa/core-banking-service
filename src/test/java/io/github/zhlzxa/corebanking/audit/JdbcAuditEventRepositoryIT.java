package io.github.zhlzxa.corebanking.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcAuditEventRepositoryIT extends AbstractIntegrationIT {

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void storesEveryFieldOfTheEvent() {
        long alice = data.createCustomer("alice-sub");
        UUID eventId = UUID.randomUUID();
        auditEventRepository.append(new AuditEvent(
                eventId,
                Instant.parse("2026-09-18T08:00:00.123456Z"),
                new AuditActor(alice, TestDataFactory.ISSUER, "alice-sub", "CUSTOMER", null, "BR-001"),
                alice,
                AuditAction.TRANSFER_REJECTED,
                "TRANSFER_REQUEST",
                "req-1",
                null,
                "req-1",
                "corr-1",
                AuditChannel.API,
                AuditOutcome.REJECTED,
                "INSUFFICIENT_BALANCE",
                Map.of("amount", "10.00", "currency", "HKD")));

        Map<String, Object> row =
                jdbc.sql("""
                        SELECT actor_user_id, actor_subject, actor_branch_code, on_behalf_of_user_id, action, outcome, reason_code, channel,
                               correlation_id, transaction_id, metadata ->> 'amount' AS amount,
                               occurred_at = TIMESTAMPTZ '2026-09-18 08:00:00.123456+00' AS exact_time
                        FROM audit_events WHERE event_id = :eventId
                        """).param("eventId", eventId).query().singleRow();
        assertThat(row)
                .containsEntry("actor_user_id", alice)
                .containsEntry("actor_subject", "alice-sub")
                .containsEntry("actor_branch_code", "BR-001")
                .containsEntry("on_behalf_of_user_id", alice)
                .containsEntry("action", "TRANSFER_REJECTED")
                .containsEntry("outcome", "REJECTED")
                .containsEntry("reason_code", "INSUFFICIENT_BALANCE")
                .containsEntry("channel", "API")
                .containsEntry("correlation_id", "corr-1")
                .containsEntry("transaction_id", null)
                .containsEntry("amount", "10.00")
                .containsEntry("exact_time", true);
    }

    @Test
    void auditEventsAreAppendOnly() {
        auditEventRepository.append(new AuditEvent(
                UUID.randomUUID(),
                Instant.now(),
                AuditActor.anonymous(),
                null,
                AuditAction.AUTHENTICATION_FAILED,
                "HTTP_ENDPOINT",
                "/transfers",
                null,
                null,
                null,
                AuditChannel.API,
                AuditOutcome.REJECTED,
                "UNAUTHENTICATED",
                Map.of()));

        assertThatThrownBy(() ->
                        jdbc.sql("UPDATE audit_events SET outcome = 'SUCCESS'").update())
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_events").update())
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("append-only");
    }
}
