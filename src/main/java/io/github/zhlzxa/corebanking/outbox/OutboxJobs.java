package io.github.zhlzxa.corebanking.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules outbox publication and cleanup. Can be switched off with {@code
 * corebanking.outbox.publisher.enabled=false}, for example in tests that drive the publisher
 * explicitly.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "corebanking.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
class OutboxJobs {

    private final OutboxPublisher publisher;

    OutboxJobs(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${corebanking.outbox.publisher.interval}")
    void publish() {
        publisher.publishDueEvents();
    }

    @Scheduled(fixedDelayString = "${corebanking.outbox.cleanup.interval}")
    void cleanUp() {
        publisher.deleteExpiredEvents();
    }
}
