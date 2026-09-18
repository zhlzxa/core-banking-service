package io.github.zhlzxa.corebanking.outbox;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Outbox publication policy.
 *
 * @param topic the broker topic transaction events are published to
 * @param lease how long a claimed batch is reserved for one publisher
 * @param retryBackoff delays before successive publication attempts; the last one repeats
 * @param sendTimeout how long to wait for the broker to acknowledge one event
 * @param retention how long published events are kept before they are deleted
 */
@Validated
@ConfigurationProperties("corebanking.outbox")
public record OutboxProperties(
        @NotBlank String topic,
        @Positive int batchSize,
        @NotNull Duration lease,
        @NotEmpty List<Duration> retryBackoff,
        @NotNull Duration sendTimeout,
        @NotNull Duration retention,
        @Positive int cleanupBatchSize) {

    public Duration backoffAfter(int attempt) {
        int index = Math.max(0, Math.min(attempt - 1, retryBackoff.size() - 1));
        return retryBackoff.get(index);
    }
}
