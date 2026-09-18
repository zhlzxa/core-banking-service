package io.github.zhlzxa.corebanking.fps;

import io.github.zhlzxa.corebanking.audit.AuditActor;
import io.github.zhlzxa.corebanking.audit.AuditChannel;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.web.CorrelationId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Resolves FPS payments whose outcome is unknown by asking FPS what happened.
 *
 * <ul>
 *   <li>accepted or rejected at FPS: the outcome is recorded, confirming or returning the money;
 *   <li>still pending at FPS: asked again later, with increasing back-off;
 *   <li>unknown to FPS: the payment never arrived and is sent again with the same end-to-end id;
 *   <li>still unresolved after the maximum number of attempts: escalated for investigation.
 * </ul>
 *
 * <p>Several instances may run at once. Each claims its batch atomically with a lease (see {@link
 * FpsPaymentRepository#claimDue}), makes its network calls without holding locks, and records every
 * outcome in a separate transaction on the locked payment. Failures are isolated per payment.
 */
@Component
public class FpsReconciler {

    private static final Logger log = LoggerFactory.getLogger(FpsReconciler.class);

    private final FpsPaymentRepository paymentRepository;
    private final FpsClient fpsClient;
    private final FpsOutcomeService outcomeService;
    private final FpsDispatcher dispatcher;
    private final FpsProperties properties;
    private final FpsMetrics metrics;
    private final Clock clock;

    FpsReconciler(
            FpsPaymentRepository paymentRepository,
            FpsClient fpsClient,
            FpsOutcomeService outcomeService,
            FpsDispatcher dispatcher,
            FpsProperties properties,
            FpsMetrics metrics,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.fpsClient = fpsClient;
        this.outcomeService = outcomeService;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Claims the payments that are due and works through them.
     *
     * @return the number of payments claimed
     */
    public int reconcileDuePayments() {
        String runId = "reconciliation-" + UUID.randomUUID();
        MDC.put(CorrelationId.MDC_KEY, runId);
        try {
            Instant now = clock.instant();
            List<Long> claimed = paymentRepository.claimDue(
                    now, now.plus(properties.reconciliationLease()), properties.reconciliationBatchSize());
            AuditContext audit = new AuditContext(AuditActor.system(), runId, AuditChannel.SYSTEM);
            for (long paymentId : claimed) {
                try {
                    reconcile(audit, paymentId);
                } catch (RuntimeException ex) {
                    log.error("Reconciliation of FPS payment failed: transactionId={}", paymentId, ex);
                }
            }
            return claimed.size();
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    private void reconcile(AuditContext audit, long paymentId) {
        FpsPayment payment = paymentRepository.findById(paymentId).orElseThrow();
        if (!payment.isUnresolved()) {
            return;
        }
        if (payment.attemptCount() > properties.maxAttempts()) {
            outcomeService.escalate(audit, paymentId);
            return;
        }
        FpsClient.Status status;
        try {
            status = metrics.time(
                    "query",
                    () -> fpsClient.query(payment.endToEndId()),
                    answer -> answer.name().toLowerCase(Locale.ROOT));
        } catch (FpsCommunicationException ex) {
            outcomeService.recordUnknown(audit, paymentId, "FPS_UNREACHABLE");
            return;
        }
        switch (status) {
            case ACCEPTED -> outcomeService.confirm(audit, paymentId);
            case REJECTED -> outcomeService.returnToCustomer(audit, paymentId, "REJECTED_BY_FPS");
            case PENDING -> outcomeService.recordUnknown(audit, paymentId, "FPS_PENDING");
            case NOT_FOUND -> dispatcher.dispatch(audit, payment);
        }
    }
}
