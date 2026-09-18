package io.github.zhlzxa.corebanking.fps;

import io.github.zhlzxa.corebanking.audit.AuditContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Second phase of an FPS payment: sends it and hands the answer to {@link FpsOutcomeService}.
 *
 * <p>The network call is made outside any database transaction. Holding row locks or a connection
 * across a call whose duration is decided by another system would let an FPS slowdown exhaust the
 * connection pool and block unrelated customers. The dispatcher refuses to run inside a transaction
 * so that this cannot be broken by accident.
 */
@Component
class FpsDispatcher {

    private static final Logger log = LoggerFactory.getLogger(FpsDispatcher.class);

    private final FpsClient fpsClient;
    private final FpsOutcomeService outcomeService;

    FpsDispatcher(FpsClient fpsClient, FpsOutcomeService outcomeService) {
        this.fpsClient = fpsClient;
        this.outcomeService = outcomeService;
    }

    /** Sends the payment, reusing its end-to-end id, and records the result. */
    FpsPayment dispatch(AuditContext audit, FpsPayment payment) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("FPS must not be called inside a database transaction");
        }
        FpsClient.Decision decision;
        try {
            decision = fpsClient.send(payment.toInstruction());
        } catch (FpsCommunicationException ex) {
            log.warn(
                    "No response from FPS; outcome unknown: transactionId={}, endToEndId={}",
                    payment.id(),
                    payment.endToEndId());
            return outcomeService.recordUnknown(audit, payment.id(), "FPS_NO_RESPONSE");
        }
        if (decision.accepted()) {
            return outcomeService.confirm(audit, payment.id());
        }
        return outcomeService.returnToCustomer(audit, payment.id(), String.valueOf(decision.rejectionReason()));
    }
}
