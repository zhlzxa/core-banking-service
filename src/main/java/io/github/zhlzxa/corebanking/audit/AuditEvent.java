package io.github.zhlzxa.corebanking.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An immutable audit record.
 *
 * @param eventId globally unique id, usable to deduplicate if events are ever exported
 * @param resourceType kind of object acted on, for example {@code TRANSFER} or {@code HTTP_ENDPOINT}
 * @param transactionId the money movement, when one exists; rejected attempts have none
 * @param reasonCode stable code explaining a non-successful outcome; never an exception message
 * @param metadata additional non-sensitive facts; must never contain credentials or tokens
 */
public record AuditEvent(
        UUID eventId,
        Instant occurredAt,
        AuditActor actor,
        AuditAction action,
        String resourceType,
        @Nullable String resourceId,
        @Nullable Long transactionId,
        @Nullable String requestId,
        @Nullable String correlationId,
        AuditChannel channel,
        AuditOutcome outcome,
        @Nullable String reasonCode,
        Map<String, Object> metadata) {

    public AuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(outcome, "outcome");
        if (outcome != AuditOutcome.SUCCESS && reasonCode == null) {
            throw new IllegalArgumentException("A reason code is required for outcome " + outcome);
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
