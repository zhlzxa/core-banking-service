package io.github.zhlzxa.corebanking.fps;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * FPS payment and reconciliation policy.
 *
 * @param retryBackoff delays before successive reconciliation attempts; the last one repeats
 * @param maxAttempts after this many reconciliation attempts without a definite outcome, the payment
 *     is escalated for investigation instead of being retried forever
 * @param reconciliationLease how long a claimed payment is reserved for one reconciler
 * @param reconciliationBatchSize how many payments one reconciliation run claims
 * @param submissionGrace how long a newly posted payment waits before reconciliation may pick it up,
 *     covering a process that stops between posting and sending
 */
@Validated
@ConfigurationProperties("corebanking.fps")
public record FpsProperties(
        @NotEmpty List<Duration> retryBackoff,
        @Positive int maxAttempts,
        @NotNull Duration reconciliationLease,
        @Positive int reconciliationBatchSize,
        @NotNull Duration submissionGrace) {

    public Duration backoffAfter(int attempt) {
        int index = Math.max(0, Math.min(attempt - 1, retryBackoff.size() - 1));
        return retryBackoff.get(index);
    }
}
