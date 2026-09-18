package io.github.zhlzxa.corebanking.transfer;

import io.github.zhlzxa.corebanking.account.AccountRepository;
import io.github.zhlzxa.corebanking.security.Permissions;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.transaction.TransactionRepository;
import io.github.zhlzxa.corebanking.transaction.TransactionType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to individual transfers for the customers involved in them. */
@Service
@Transactional(readOnly = true)
public class TransferQueryService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransferQueryService(TransactionRepository transactionRepository, AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Returns a transfer if the customer owns its source or its destination account.
     *
     * @throws TransferNotFoundException if the transfer does not exist or does not involve the
     *     customer; both cases are indistinguishable to the caller
     */
    @PreAuthorize(Permissions.CUSTOMER_READ_ACCOUNTS)
    public BankTransaction getTransfer(long customerId, long transactionId) {
        return transactionRepository
                .findById(transactionId)
                .filter(transaction -> transaction.type() == TransactionType.TRANSFER)
                .filter(transaction -> involves(customerId, transaction))
                .orElseThrow(TransferNotFoundException::new);
    }

    private boolean involves(long customerId, BankTransaction transaction) {
        return accountRepository
                        .findOwned(transaction.fromAccountId(), customerId)
                        .isPresent()
                || accountRepository
                        .findOwned(transaction.toAccountId(), customerId)
                        .isPresent();
    }
}
