package io.github.zhlzxa.corebanking.audit;

import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcAuditEventRepository implements AuditEventRepository {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    JdbcAuditEventRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void append(AuditEvent event) {
        jdbc.sql("""
                        INSERT INTO audit_events
                            (event_id, occurred_at, actor_user_id, actor_issuer, actor_subject, actor_role,
                             action, resource_type, resource_id, transaction_id, request_id, correlation_id,
                             channel, outcome, reason_code, metadata)
                        VALUES
                            (:eventId, :occurredAt, :actorUserId, :actorIssuer, :actorSubject, :actorRole,
                             :action, :resourceType, :resourceId, :transactionId, :requestId, :correlationId,
                             :channel, :outcome, :reasonCode, CAST(:metadata AS JSONB))
                        """)
                .param("eventId", event.eventId())
                .param("occurredAt", event.occurredAt().atOffset(ZoneOffset.UTC))
                .param("actorUserId", event.actor().userId())
                .param("actorIssuer", event.actor().issuer())
                .param("actorSubject", event.actor().subject())
                .param("actorRole", event.actor().role())
                .param("action", event.action().name())
                .param("resourceType", event.resourceType())
                .param("resourceId", event.resourceId())
                .param("transactionId", event.transactionId())
                .param("requestId", event.requestId())
                .param("correlationId", event.correlationId())
                .param("channel", event.channel().name())
                .param("outcome", event.outcome().name())
                .param("reasonCode", event.reasonCode())
                .param("metadata", jsonMapper.writeValueAsString(event.metadata()))
                .update();
    }
}
