package io.github.zhlzxa.corebanking.outbox;

import java.util.UUID;

/** Delivers one event to the message broker and returns only once the broker has acknowledged it. */
public interface EventPublisher {

    /**
     * @throws RuntimeException if the broker did not acknowledge the event; the outbox will retry
     */
    void publish(UUID eventId, String eventType, String key, String payload);
}
