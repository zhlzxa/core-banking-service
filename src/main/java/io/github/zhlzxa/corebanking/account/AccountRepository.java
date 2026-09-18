package io.github.zhlzxa.corebanking.account;

import java.math.BigDecimal;
import java.util.Optional;

/** Persistence operations on accounts. All mutating methods require an active transaction. */
public interface AccountRepository {

    Optional<Account> findById(long accountId);

    /**
     * Loads an account and takes a row-level write lock ({@code SELECT ... FOR UPDATE}) that is held
     * until the surrounding transaction ends.
     *
     * <p>Callers locking more than one account must acquire the locks in ascending id order to
     * prevent deadlocks between concurrent transfers in opposite directions.
     */
    Optional<Account> findByIdForUpdate(long accountId);

    /**
     * Decreases the balance.
     *
     * @throws IllegalStateException if the account does not exist or the balance would become
     *     negative; callers are expected to have checked both under a row lock
     */
    void debit(long accountId, BigDecimal amount);

    /**
     * Increases the balance.
     *
     * @throws IllegalStateException if the account does not exist
     */
    void credit(long accountId, BigDecimal amount);
}
