package io.github.zhlzxa.corebanking.account;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.JdbcUpdateAffectedIncorrectNumberOfRowsException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccountRepository implements AccountRepository {

    private static final String COLUMNS = """
            SELECT id, user_id, currency, balance, status, status_reason,
                   per_transaction_limit, daily_transfer_limit
            FROM accounts
            """;

    private final JdbcClient jdbc;

    JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> findById(long accountId) {
        return jdbc.sql(COLUMNS + " WHERE id = :id")
                .param("id", accountId)
                .query(JdbcAccountRepository::mapAccount)
                .optional();
    }

    @Override
    public List<Account> findByOwner(long userId) {
        return jdbc.sql(COLUMNS + " WHERE user_id = :userId ORDER BY id")
                .param("userId", userId)
                .query(JdbcAccountRepository::mapAccount)
                .list();
    }

    @Override
    public Optional<Account> findOwned(long accountId, long userId) {
        return jdbc.sql(COLUMNS + " WHERE id = :id AND user_id = :userId")
                .param("id", accountId)
                .param("userId", userId)
                .query(JdbcAccountRepository::mapAccount)
                .optional();
    }

    @Override
    public long findInternalAccountId(String code) {
        return jdbc.sql("SELECT id FROM accounts WHERE account_type = 'INTERNAL' AND code = :code")
                .param("code", code)
                .query(Long.class)
                .optional()
                .orElseThrow(AccountNotFoundException::new);
    }

    @Override
    public Optional<Account> findByIdForUpdate(long accountId) {
        return jdbc.sql(COLUMNS + " WHERE id = :id FOR UPDATE")
                .param("id", accountId)
                .query(JdbcAccountRepository::mapAccount)
                .optional();
    }

    @Override
    public void debit(long accountId, BigDecimal amount) {
        // The balance predicate duplicates the service-level check on purpose: if a caller ever
        // debits a customer account without holding the row lock, the update fails instead of
        // overdrawing. Internal accounts carry the bank's side and may go negative.
        int updated =
                jdbc.sql("""
                        UPDATE accounts
                        SET balance = balance - :amount, updated_at = now()
                        WHERE id = :id AND (balance >= :amount OR account_type = 'INTERNAL')
                        """).param("id", accountId).param("amount", amount).update();
        if (updated != 1) {
            throw new JdbcUpdateAffectedIncorrectNumberOfRowsException("debit account " + accountId, 1, updated);
        }
    }

    @Override
    public void credit(long accountId, BigDecimal amount) {
        int updated =
                jdbc.sql("""
                        UPDATE accounts
                        SET balance = balance + :amount, updated_at = now()
                        WHERE id = :id
                        """).param("id", accountId).param("amount", amount).update();
        if (updated != 1) {
            throw new JdbcUpdateAffectedIncorrectNumberOfRowsException("credit account " + accountId, 1, updated);
        }
    }

    @Override
    public void updateStatus(long accountId, AccountStatus status, @Nullable StatusReason reason) {
        int updated = jdbc.sql("""
                        UPDATE accounts SET status = :status, status_reason = :reason, updated_at = now()
                        WHERE id = :id
                        """)
                .param("id", accountId)
                .param("status", status.name())
                .param("reason", reason == null ? null : reason.name())
                .update();
        if (updated != 1) {
            throw new JdbcUpdateAffectedIncorrectNumberOfRowsException(
                    "update status of account " + accountId, 1, updated);
        }
    }

    @Override
    public void updateLimits(
            long accountId, @Nullable BigDecimal perTransactionLimit, @Nullable BigDecimal dailyLimit) {
        int updated = jdbc.sql("""
                        UPDATE accounts
                        SET per_transaction_limit = :perTransaction, daily_transfer_limit = :daily, updated_at = now()
                        WHERE id = :id
                        """)
                .param("id", accountId)
                .param("perTransaction", perTransactionLimit)
                .param("daily", dailyLimit)
                .update();
        if (updated != 1) {
            throw new JdbcUpdateAffectedIncorrectNumberOfRowsException(
                    "update limits of account " + accountId, 1, updated);
        }
    }

    private static Account mapAccount(ResultSet rs, int rowNum) throws SQLException {
        String reason = rs.getString("status_reason");
        return new Account(
                rs.getLong("id"),
                rs.getObject("user_id", Long.class),
                rs.getString("currency"),
                rs.getBigDecimal("balance"),
                AccountStatus.valueOf(rs.getString("status")),
                reason == null ? null : StatusReason.valueOf(reason),
                rs.getBigDecimal("per_transaction_limit"),
                rs.getBigDecimal("daily_transfer_limit"));
    }
}
