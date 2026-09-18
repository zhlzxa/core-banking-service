package io.github.zhlzxa.corebanking.fps;

import io.github.zhlzxa.corebanking.account.AccountRepository;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.fps.FpsPayment.ExternalStatus;
import io.github.zhlzxa.corebanking.security.Permissions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pays accounts at other banks through FPS.
 *
 * <p>A payment runs in three phases: the customer is debited in a local transaction, the payment is
 * sent outside any transaction, and the answer is recorded in another local transaction. If FPS does
 * not answer, the payment remains {@code PROCESSING} and {@link FpsReconciler} resolves it later.
 * This method is deliberately not transactional.
 */
@Service
@EnableConfigurationProperties(FpsProperties.class)
public class FpsPaymentService {

    private final FpsPostingService postingService;
    private final FpsDispatcher dispatcher;
    private final FpsPaymentRepository paymentRepository;
    private final AccountRepository accountRepository;

    public FpsPaymentService(
            FpsPostingService postingService,
            FpsDispatcher dispatcher,
            FpsPaymentRepository paymentRepository,
            AccountRepository accountRepository) {
        this.postingService = postingService;
        this.dispatcher = dispatcher;
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Debits the customer and sends the payment. A repeated request id returns the payment in its
     * current state; a payment that was posted but never sent is sent now.
     *
     * @return the payment: {@code COMPLETED}, {@code REVERSED}, or {@code PROCESSING} if the outcome
     *     is not yet known
     */
    @PreAuthorize(Permissions.CUSTOMER_TRANSFER)
    public FpsPayment pay(AuditContext audit, FpsPaymentCommand command) {
        FpsPayment payment = postingService.initiate(audit, command);
        if (!payment.isUnresolved() || payment.externalStatus() != ExternalStatus.NOT_SENT) {
            return payment;
        }
        return dispatcher.dispatch(audit, payment);
    }

    /**
     * @throws FpsPaymentNotFoundException if the payment does not exist or was not sent from one of
     *     the customer's accounts
     */
    @Transactional(readOnly = true)
    @PreAuthorize(Permissions.CUSTOMER_READ_ACCOUNTS)
    public FpsPayment getPayment(long customerId, long paymentId) {
        return paymentRepository
                .findById(paymentId)
                .filter(payment -> accountRepository
                        .findOwned(payment.fromAccountId(), customerId)
                        .isPresent())
                .orElseThrow(FpsPaymentNotFoundException::new);
    }
}
