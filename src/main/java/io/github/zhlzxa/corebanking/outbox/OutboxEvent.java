package io.github.zhlzxa.corebanking.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An integration event to be delivered to other systems.
 *
 * @param eventId unique id; consumers use it to recognise redeliveries
 * @param aggregateId id of the business object the event is about; used as the message key, so
 *     events about the same object go to the same partition
 * @param payload the event body; must not contain personal data, credentials or external account
 *     numbers, because events are copied to systems with their own retention rules
 */
public record OutboxEvent(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        Map<String, Object> payload,
        Instant occurredAt) {

    public OutboxEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        payload = Map.copyOf(payload);
    }
}
