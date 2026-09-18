package io.github.zhlzxa.corebanking.fps;

import io.github.zhlzxa.corebanking.account.DailyTransferUsageRepository;
import io.github.zhlzxa.corebanking.audit.AuditAction;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.audit.AuditEventFactory;
import io.github.zhlzxa.corebanking.audit.AuditEventRepository;
import io.github.zhlzxa.corebanking.common.time.BusinessCalendar;
import io.github.zhlzxa.corebanking.fps.FpsPayment.ExternalStatus;
import io.github.zhlzxa.corebanking.posting.Reversals;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.transaction.TransactionRepository;
import io.github.zhlzxa.corebanking.transaction.TransactionStatus;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records what became of an FPS payment, each outcome in its own local transaction.
 *
 * <p>Every method locks the payment row and acts only while the payment is still {@code
 * PROCESSING}. A second report of the same outcome, for example from the synchronous response and
 * from reconciliation, therefore has no further effect.
 *
 * <p>Money that has left the customer's account is never returned just because FPS did not answer:
 * only an explicit rejection by FPS triggers a reversal. See ADR-0009.
 */
@Service
class FpsOutcomeService {

    private static final Logger log = LoggerFactory.getLogger(FpsOutcomeService.class);

    private final FpsPaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final Reversals reversals;
    private final DailyTransferUsageRepository dailyTransferUsageRepository;
    private final BusinessCalendar businessCalendar;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventFactory auditEventFactory;
    private final FpsProperties properties;
    private final Clock clock;

    FpsOutcomeService(
            FpsPaymentRepository paymentRepository,
            TransactionRepository transactionRepository,
            Reversals reversals,
            DailyTransferUsageRepository dailyTransferUsageRepository,
            BusinessCalendar businessCalendar,
            AuditEventRepository auditEventRepository,
            AuditEventFactory auditEventFactory,
            FpsProperties properties,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
        this.reversals = reversals;
        this.dailyTransferUsageRepository = dailyTransferUsageRepository;
        this.businessCalendar = businessCalendar;
        this.auditEventRepository = auditEventRepository;
        this.auditEventFactory = auditEventFactory;
        this.properties = properties;
        this.clock = clock;
    }

    /** FPS accepted the payment: it is final. */
    @Transactional
    public FpsPayment confirm(AuditContext audit, long paymentId) {
        FpsPayment payment = lock(paymentId);
        if (!payment.isUnresolved()) {
            return payment;
        }
        paymentRepository.recordOutcome(paymentId, TransactionStatus.COMPLETED, ExternalStatus.ACCEPTED, null, null);
        record(audit, AuditAction.FPS_PAYMENT_CONFIRMED, payment, Map.of("endToEndId", payment.endToEndId()));
        return reload(paymentId);
    }

    /**
     * FPS rejected the payment: the money is returned to the customer with an offsetting reversal
     * transaction, and the payment's share of the daily limit is given back.
     */
    @Transactional
    public FpsPayment returnToCustomer(AuditContext audit, long paymentId, String reason) {
        FpsPayment payment = lock(paymentId);
        if (!payment.isUnresolved()) {
            return payment;
        }
        BankTransaction original = transactionRepository.findById(paymentId).orElseThrow();
        BankTransaction reversal = reversals.reverse(original);
        paymentRepository.linkReversal(reversal.id(), paymentId);
        dailyTransferUsageRepository.release(
                payment.fromAccountId(), businessCalendar.dayOf(payment.createdAt()), payment.amount());
        paymentRepository.recordOutcome(paymentId, TransactionStatus.REVERSED, ExternalStatus.REJECTED, null, reason);
        record(
                audit,
                AuditAction.FPS_PAYMENT_RETURNED,
                payment,
                Map.of("reason", reason, "reversalTransactionId", reversal.id()));
        return reload(paymentId);
    }

    /**
     * No definite answer: the payment stays {@code PROCESSING} and is scheduled for the next
     * reconciliation attempt. The customer is not refunded.
     */
    @Transactional
    public FpsPayment recordUnknown(AuditContext audit, long paymentId, String reason) {
        FpsPayment payment = lock(paymentId);
        if (!payment.isUnresolved()) {
            return payment;
        }
        paymentRepository.recordOutcome(
                paymentId,
                TransactionStatus.PROCESSING,
                ExternalStatus.UNKNOWN,
                clock.instant().plus(properties.backoffAfter(payment.attemptCount())),
                reason);
        if (payment.externalStatus() != ExternalStatus.UNKNOWN) {
            record(audit, AuditAction.FPS_PAYMENT_OUTCOME_UNKNOWN, payment, Map.of("reason", reason));
        }
        return reload(paymentId);
    }

    /**
     * Automatic resolution has given up. The payment stays debited and is handed to people, who
     * must find out from FPS what happened; neither confirming nor refunding it automatically would
     * be safe.
     */
    @Transactional
    public FpsPayment escalate(AuditContext audit, long paymentId) {
        FpsPayment payment = lock(paymentId);
        if (!payment.isUnresolved()) {
            return payment;
        }
        paymentRepository.recordOutcome(
                paymentId,
                TransactionStatus.NEEDS_INVESTIGATION,
                payment.externalStatus(),
                null,
                "MAX_ATTEMPTS_EXCEEDED");
        record(audit, AuditAction.FPS_PAYMENT_NEEDS_INVESTIGATION, payment, Map.of("attempts", payment.attemptCount()));
        log.error(
                "FPS payment needs manual investigation: transactionId={}, endToEndId={}, attempts={}",
                paymentId,
                payment.endToEndId(),
                payment.attemptCount());
        return reload(paymentId);
    }

    private FpsPayment lock(long paymentId) {
        return paymentRepository.findByIdForUpdate(paymentId).orElseThrow(FpsPaymentNotFoundException::new);
    }

    private FpsPayment reload(long paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow(FpsPaymentNotFoundException::new);
    }

    private void record(AuditContext audit, AuditAction action, FpsPayment payment, Map<String, Object> details) {
        auditEventRepository.append(
                auditEventFactory.paymentOutcome(audit, action, payment.id(), payment.initiatedByUserId(), details));
    }
}
