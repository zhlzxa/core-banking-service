package io.github.zhlzxa.corebanking.fps;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link FpsReconciler} periodically. Can be switched off with {@code
 * corebanking.fps.reconciliation.enabled=false}, for example in tests that drive reconciliation
 * explicitly.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "corebanking.fps.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
class FpsReconciliationJob {

    private final FpsReconciler reconciler;

    FpsReconciliationJob(FpsReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @Scheduled(fixedDelayString = "${corebanking.fps.reconciliation.interval}")
    void run() {
        reconciler.reconcileDuePayments();
    }
}
