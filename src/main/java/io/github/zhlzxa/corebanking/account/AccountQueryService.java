package io.github.zhlzxa.corebanking.account;

import io.github.zhlzxa.corebanking.security.Permissions;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.transaction.HistoryCursor;
import io.github.zhlzxa.corebanking.transaction.TransactionRepository;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to a customer's own accounts.
 *
 * <p>Every query is scoped to the calling customer. An account that exists but belongs to someone
 * else is reported as not found, never as forbidden and never as an empty result, so that account
 * identifiers cannot be probed.
 */
@Service
@Transactional(readOnly = true)
public class AccountQueryService {

    public static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountQueryService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @PreAuthorize(Permissions.CUSTOMER_READ_ACCOUNTS)
    public List<Account> listAccounts(long customerId) {
        return accountRepository.findByOwner(customerId);
    }

    /**
     * @throws AccountNotFoundException if the account does not exist or is not the customer's
     */
    @PreAuthorize(Permissions.CUSTOMER_READ_ACCOUNTS)
    public Account getAccount(long customerId, long accountId) {
        return accountRepository.findOwned(accountId, customerId).orElseThrow(AccountNotFoundException::new);
    }

    /**
     * Returns one page of the account's history, newest first, using keyset pagination.
     *
     * <p>One row more than requested is fetched to learn whether another page exists without a
     * separate count query. The cursor is taken from the last row actually returned.
     *
     * @param cursor value of {@code nextCursor} from the previous page, or {@code null} for the first
     * @throws AccountNotFoundException if the account does not exist or is not the customer's
     * @throws io.github.zhlzxa.corebanking.transaction.InvalidCursorException if the cursor is invalid
     */
    @PreAuthorize(Permissions.CUSTOMER_READ_ACCOUNTS)
    public TransactionHistoryPage history(long customerId, long accountId, @Nullable String cursor, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Account account = getAccount(customerId, accountId);
        HistoryCursor after = cursor == null ? null : HistoryCursor.decode(cursor);

        List<BankTransaction> rows = transactionRepository.findHistory(account.id(), after, size + 1);
        if (rows.size() <= size) {
            return new TransactionHistoryPage(account.id(), rows, null);
        }
        List<BankTransaction> page = rows.subList(0, size);
        BankTransaction last = page.getLast();
        String next = new HistoryCursor(last.createdAt(), last.id()).encode();
        return new TransactionHistoryPage(account.id(), List.copyOf(page), next);
    }
}
