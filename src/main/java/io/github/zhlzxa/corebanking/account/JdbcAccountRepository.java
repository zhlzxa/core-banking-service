package io.github.zhlzxa.corebanking.account;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccountRepository implements AccountRepository {

    private final JdbcClient jdbc;

    JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> findById(long accountId) {
        return jdbc.sql("SELECT id, user_id, currency, balance FROM accounts WHERE id = :id")
                .param("id", accountId)
                .query(JdbcAccountRepository::mapAccount)
                .optional();
    }

    @Override
    public Optional<Account> findByIdForUpdate(long accountId) {
        return jdbc.sql("SELECT id, user_id, currency, balance FROM accounts WHERE id = :id FOR UPDATE")
                .param("id", accountId)
                .query(JdbcAccountRepository::mapAccount)
                .optional();
    }

    @Override
    public void debit(long accountId, BigDecimal amount) {
        // The balance predicate duplicates the service-level check on purpose: if a caller ever
        // debits without holding the row lock, the update fails instead of overdrawing.
        int updated =
                jdbc.sql("""
                        UPDATE accounts
                        SET balance = balance - :amount, updated_at = now()
                        WHERE id = :id AND balance >= :amount
                        """).param("id", accountId).param("amount", amount).update();
        if (updated != 1) {
            throw new IllegalStateException("Debit was not applied to account " + accountId);
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
            throw new IllegalStateException("Credit was not applied to account " + accountId);
        }
    }

    private static Account mapAccount(ResultSet rs, int rowNum) throws SQLException {
        return new Account(
                rs.getLong("id"),
                rs.getObject("user_id", Long.class),
                rs.getString("currency"),
                rs.getBigDecimal("balance"));
    }
}
