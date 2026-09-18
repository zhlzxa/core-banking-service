package io.github.zhlzxa.corebanking.posting;

import io.github.zhlzxa.corebanking.account.Account;
import io.github.zhlzxa.corebanking.account.AccountNotFoundException;
import io.github.zhlzxa.corebanking.account.AccountRepository;
import org.springframework.stereotype.Component;

/**
 * Acquires row locks on accounts in a deadlock-free order.
 *
 * <p>Every operation that locks more than one account must go through this class. Locks are taken
 * in ascending id order regardless of the direction of the movement, so all transactions acquire
 * them in the same global order and can never wait for each other in a cycle. See ADR-0001.
 */
@Component
public class AccountLocks {

    private final AccountRepository accountRepository;

    public AccountLocks(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Locks both accounts for the rest of the current transaction.
     *
     * @return the locked accounts in the order they were requested, not the order they were locked
     * @throws AccountNotFoundException if either account does not exist
     */
    public LockedPair lockPair(long firstAccountId, long secondAccountId) {
        long lowerId = Math.min(firstAccountId, secondAccountId);
        long higherId = Math.max(firstAccountId, secondAccountId);
        Account lower = lock(lowerId);
        Account higher = lock(higherId);
        return lower.id() == firstAccountId ? new LockedPair(lower, higher) : new LockedPair(higher, lower);
    }

    /**
     * @throws AccountNotFoundException if the account does not exist
     */
    public Account lock(long accountId) {
        return accountRepository.findByIdForUpdate(accountId).orElseThrow(AccountNotFoundException::new);
    }

    /** Two locked accounts, in the order the caller named them. */
    public record LockedPair(Account first, Account second) {}
}
