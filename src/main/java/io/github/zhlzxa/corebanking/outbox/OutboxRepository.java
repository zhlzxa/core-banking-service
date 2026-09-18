package io.github.zhlzxa.corebanking.outbox;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** Storage of outgoing integration events. */
@Repository
public class OutboxRepository {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    public OutboxRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Stores an event in the caller's transaction. It is published only if that transaction
     * commits, and it is never lost if it does. The event is due at once; the due time is taken from
     * the application clock, like every time the publisher compares it with, so clock skew between
     * the application and the database cannot delay publication.
     */
    public void append(OutboxEvent event) {
        jdbc.sql("""
                        INSERT INTO outbox_events
                            (event_id, aggregate_type, aggregate_id, event_type, payload, occurred_at, next_attempt_at)
                        VALUES (:eventId, :aggregateType, :aggregateId, :eventType, CAST(:payload AS JSONB),
                                :occurredAt, :occurredAt)
                        """)
                .param("eventId", event.eventId())
                .param("aggregateType", event.aggregateType())
                .param("aggregateId", event.aggregateId())
                .param("eventType", event.eventType())
                .param("payload", jsonMapper.writeValueAsString(event.payload()))
                .param("occurredAt", event.occurredAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    /**
     * Claims up to {@code limit} unpublished events that are due, in insertion order, for exclusive
     * publication until {@code leaseUntil}. One statement selects with {@code FOR UPDATE SKIP LOCKED}
     * and commits the lease, so concurrent publishers take disjoint events and no lock is held while
     * the broker is called.
     */
    List<PendingEvent> claimDue(Instant now, Instant leaseUntil, int limit) {
        return jdbc.sql("""
                        UPDATE outbox_events
                        SET next_attempt_at = :leaseUntil, attempt_count = attempt_count + 1
                        WHERE id IN (
                            SELECT id FROM outbox_events
                            WHERE published_at IS NULL AND next_attempt_at <= :now
                            ORDER BY id
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED)
                        RETURNING id, event_id, aggregate_id, event_type, payload::text AS payload, attempt_count
                        """)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("leaseUntil", leaseUntil.atOffset(ZoneOffset.UTC))
                .param("limit", limit)
                .query((rs, rowNum) -> new PendingEvent(
                        rs.getLong("id"),
                        rs.getObject("event_id", UUID.class),
                        rs.getString("aggregate_id"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getInt("attempt_count")))
                .list();
    }

    void markPublished(long id, Instant publishedAt) {
        jdbc.sql("UPDATE outbox_events SET published_at = :at, last_error = NULL WHERE id = :id")
                .param("id", id)
                .param("at", publishedAt.atOffset(ZoneOffset.UTC))
                .update();
    }

    void scheduleRetry(long id, Instant nextAttemptAt, String error) {
        jdbc.sql("UPDATE outbox_events SET next_attempt_at = :next, last_error = :error WHERE id = :id")
                .param("id", id)
                .param("next", nextAttemptAt.atOffset(ZoneOffset.UTC))
                .param("error", error.length() > 500 ? error.substring(0, 500) : error)
                .update();
    }

    /** Deletes at most {@code batchSize} events published before {@code cutoff}; returns how many. */
    int deletePublishedBefore(Instant cutoff, int batchSize) {
        return jdbc.sql("""
                        DELETE FROM outbox_events
                        WHERE id IN (
                            SELECT id FROM outbox_events
                            WHERE published_at < :cutoff
                            LIMIT :batchSize)
                        """)
                .param("cutoff", cutoff.atOffset(ZoneOffset.UTC))
                .param("batchSize", batchSize)
                .update();
    }

    /** Number of events waiting to be published; served by the partial index. */
    public long countUnpublished() {
        return jdbc.sql("SELECT count(*) FROM outbox_events WHERE published_at IS NULL")
                .query(Long.class)
                .single();
    }

    /** When the oldest unpublished event occurred, if any; the key indicator of a stuck publisher. */
    public Optional<Instant> oldestUnpublishedAt() {
        return jdbc.sql("SELECT min(occurred_at) FROM outbox_events WHERE published_at IS NULL")
                .query((rs, rowNum) -> rs.getObject(1, OffsetDateTime.class))
                .optional()
                .map(OffsetDateTime::toInstant);
    }

    /** An event claimed for publication. The payload is the stored JSON document. */
    record PendingEvent(
            long id, UUID eventId, String aggregateId, String eventType, String payload, int attemptCount) {}
}
