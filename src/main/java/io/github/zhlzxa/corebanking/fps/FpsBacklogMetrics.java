package io.github.zhlzxa.corebanking.fps;

import io.github.zhlzxa.corebanking.transaction.TransactionStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * FPS payments that are waiting for an outcome, and those waiting for a person.
 *
 * <p>The gauges query the database when they are scraped. Each query is served by a partial index
 * over the few unresolved payments, and the scrape interval bounds how often it runs.
 */
@Component
class FpsBacklogMetrics implements MeterBinder {

    private final FpsPaymentRepository paymentRepository;
    private final Clock clock;

    FpsBacklogMetrics(FpsPaymentRepository paymentRepository, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        register(registry, TransactionStatus.PROCESSING, "processing", "FPS payments awaiting an outcome");
        register(
                registry,
                TransactionStatus.NEEDS_INVESTIGATION,
                "needs.investigation",
                "FPS payments escalated for manual investigation");
    }

    private void register(MeterRegistry registry, TransactionStatus status, String name, String description) {
        Gauge.builder(
                        "corebanking.fps.payments." + name,
                        () -> paymentRepository.backlog(status).count())
                .description(description)
                .register(registry);
        Gauge.builder("corebanking.fps.payments." + name + ".oldest.age", () -> oldestAgeSeconds(status))
                .description("Age of the oldest of the " + description)
                .baseUnit("seconds")
                .register(registry);
    }

    private double oldestAgeSeconds(TransactionStatus status) {
        Instant oldest = paymentRepository.backlog(status).oldestCreatedAt();
        return oldest == null ? 0 : Duration.between(oldest, clock.instant()).toMillis() / 1000.0;
    }
}
