package io.github.zhlzxa.corebanking.outbox;

import io.github.zhlzxa.corebanking.outbox.OutboxRepository.PendingEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Delivers outbox events to the broker, at least once.
 *
 * <p>An event is marked published only after the broker has acknowledged it. If the process stops in
 * between, the event is delivered again after its lease expires; consumers deduplicate by event id.
 * A failed delivery is retried with back-off without holding up the other events of the batch.
 * Ordering is best effort: events are claimed in insertion order, but a retried event can arrive
 * after later ones, so consumers must not rely on a global order.
 */
@Component
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final EventPublisher eventPublisher;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            EventPublisher eventPublisher,
            OutboxProperties properties,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Claims one batch of due events and publishes them.
     *
     * @return the number of events published successfully
     */
    public int publishDueEvents() {
        Instant now = clock.instant();
        List<PendingEvent> batch = outboxRepository.claimDue(now, now.plus(properties.lease()), properties.batchSize());
        int published = 0;
        for (PendingEvent event : batch) {
            try {
                eventPublisher.publish(event.eventId(), event.eventType(), event.aggregateId(), event.payload());
                outboxRepository.markPublished(event.id(), clock.instant());
                published++;
            } catch (RuntimeException ex) {
                Instant next = clock.instant().plus(properties.backoffAfter(event.attemptCount()));
                outboxRepository.scheduleRetry(
                        event.id(), next, ex.getClass().getSimpleName() + ": " + ex.getMessage());
                log.warn(
                        "Outbox event not published; will retry: eventId={}, attempt={}",
                        event.eventId(),
                        event.attemptCount(),
                        ex);
            }
        }
        return published;
    }

    /**
     * Deletes published events older than the retention period, in bounded batches so that the
     * table is never locked for long.
     *
     * @return the number of events deleted
     */
    public int deleteExpiredEvents() {
        Instant cutoff = clock.instant().minus(properties.retention());
        int total = 0;
        int deleted;
        do {
            deleted = outboxRepository.deletePublishedBefore(cutoff, properties.cleanupBatchSize());
            total += deleted;
        } while (deleted == properties.cleanupBatchSize());
        return total;
    }
}
