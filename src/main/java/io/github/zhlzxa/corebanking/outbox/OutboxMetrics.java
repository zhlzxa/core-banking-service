package io.github.zhlzxa.corebanking.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Backlog of the outbox. A growing backlog, or an oldest unpublished event that keeps ageing, means
 * that consumers are falling behind the ledger: the broker is unreachable or the publisher is not
 * running. Both gauges are served by the partial index on unpublished events.
 */
@Component
class OutboxMetrics implements MeterBinder {

    private final OutboxRepository outboxRepository;
    private final Clock clock;

    OutboxMetrics(OutboxRepository outboxRepository, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("corebanking.outbox.unpublished", outboxRepository::countUnpublished)
                .description("Integration events not yet acknowledged by the broker")
                .register(registry);
        Gauge.builder("corebanking.outbox.oldest.unpublished.age", this::oldestAgeSeconds)
                .description("Age of the oldest integration event not yet acknowledged by the broker")
                .baseUnit("seconds")
                .register(registry);
    }

    private double oldestAgeSeconds() {
        return outboxRepository
                .oldestUnpublishedAt()
                .map(oldest -> Duration.between(oldest, clock.instant()).toMillis() / 1000.0)
                .orElse(0.0);
    }
}
