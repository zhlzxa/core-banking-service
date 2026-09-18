package io.github.zhlzxa.corebanking.payee;

import io.github.zhlzxa.corebanking.audit.AuditAction;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.audit.AuditEventFactory;
import io.github.zhlzxa.corebanking.audit.AuditEventRepository;
import io.github.zhlzxa.corebanking.common.error.StaleVersionException;
import io.github.zhlzxa.corebanking.security.Permissions;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages a customer's saved payees.
 *
 * <p>Every operation is scoped to the calling customer; another customer's payee is reported as
 * not found. Changes are audited inside the same transaction, so a change without an audit record
 * cannot be committed.
 *
 * <p>Renames use optimistic concurrency: the client sends the version it last read. A mismatch, or
 * a concurrent update detected by the persistence layer, is reported as
 * {@code CONCURRENT_MODIFICATION} instead of silently overwriting the other change.
 */
@Service
public class PayeeService {

    private final PayeeRepository payeeRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventFactory auditEventFactory;

    public PayeeService(
            PayeeRepository payeeRepository,
            AuditEventRepository auditEventRepository,
            AuditEventFactory auditEventFactory) {
        this.payeeRepository = payeeRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventFactory = auditEventFactory;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(Permissions.CUSTOMER_READ_PAYEES)
    public List<Payee> list(long customerId) {
        return payeeRepository.findByOwnerIdOrderByNicknameAscIdAsc(customerId);
    }

    /**
     * @throws PayeeAlreadyExistsException if the customer already saved this destination
     */
    @Transactional
    @PreAuthorize(Permissions.CUSTOMER_MANAGE_PAYEES)
    public Payee add(AuditContext audit, long customerId, NewPayee newPayee) {
        if (payeeRepository.existsDestination(customerId, newPayee.accountNumber(), newPayee.bankCode())) {
            throw new PayeeAlreadyExistsException();
        }
        Payee payee;
        try {
            // Flush immediately so that a concurrent insert of the same destination surfaces here,
            // as a unique constraint violation, rather than at commit time.
            payee = payeeRepository.saveAndFlush(new Payee(
                    customerId,
                    newPayee.nickname(),
                    newPayee.accountNumber(),
                    newPayee.bankCode(),
                    newPayee.currency()));
        } catch (DataIntegrityViolationException e) {
            throw new PayeeAlreadyExistsException();
        }
        auditEventRepository.append(auditEventFactory.payeeChanged(audit, AuditAction.PAYEE_ADDED, payee.getId()));
        return payee;
    }

    /**
     * @param expectedVersion version the client last read
     * @throws PayeeNotFoundException if the payee does not exist or is not the customer's
     * @throws StaleVersionException if the payee changed since the client read it
     */
    @Transactional
    @PreAuthorize(Permissions.CUSTOMER_MANAGE_PAYEES)
    public Payee rename(AuditContext audit, long customerId, long payeeId, String nickname, long expectedVersion) {
        Payee payee = payeeRepository.findByIdAndOwnerId(payeeId, customerId).orElseThrow(PayeeNotFoundException::new);
        if (payee.getVersion() != expectedVersion) {
            throw new StaleVersionException();
        }
        payee.rename(nickname);
        // Dirty checking issues the UPDATE; flushing now applies the version check before the audit
        // event is written and returns the incremented version to the caller.
        payeeRepository.flush();
        auditEventRepository.append(auditEventFactory.payeeChanged(audit, AuditAction.PAYEE_RENAMED, payeeId));
        return payee;
    }

    /**
     * @throws PayeeNotFoundException if the payee does not exist or is not the customer's
     */
    @Transactional
    @PreAuthorize(Permissions.CUSTOMER_MANAGE_PAYEES)
    public void remove(AuditContext audit, long customerId, long payeeId) {
        Payee payee = payeeRepository.findByIdAndOwnerId(payeeId, customerId).orElseThrow(PayeeNotFoundException::new);
        payeeRepository.delete(payee);
        auditEventRepository.append(auditEventFactory.payeeChanged(audit, AuditAction.PAYEE_REMOVED, payeeId));
    }
}
