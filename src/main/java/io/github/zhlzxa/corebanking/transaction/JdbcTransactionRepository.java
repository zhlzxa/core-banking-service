package io.github.zhlzxa.corebanking.transaction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTransactionRepository implements TransactionRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, request_id, initiated_by_user_id, transaction_type, status, from_account_id, to_account_id,
                   amount, currency, created_at
            FROM transactions
            """;

    private final JdbcClient jdbc;

    JdbcTransactionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Long> insertPendingIfAbsent(NewTransaction transaction) {
        // ON CONFLICT DO NOTHING instead of catching a duplicate-key exception: in PostgreSQL a
        // failed statement aborts the whole transaction, which would make it impossible to go on
        // and read the existing row within the same unit of work.
        return jdbc.sql("""
                        INSERT INTO transactions
                            (request_id, initiated_by_user_id, transaction_type, status,
                             from_account_id, to_account_id, amount, currency)
                        VALUES (:requestId, :initiatedBy, :type, 'PENDING', :from, :to, :amount, :currency)
                        ON CONFLICT (request_id) DO NOTHING
                        RETURNING id
                        """)
                .param("requestId", transaction.requestId())
                .param("initiatedBy", transaction.initiatedByUserId())
                .param("type", transaction.type().name())
                .param("from", transaction.fromAccountId())
                .param("to", transaction.toAccountId())
                .param("amount", transaction.amount())
                .param("currency", transaction.currency())
                .query(Long.class)
                .optional();
    }

    @Override
    public Optional<BankTransaction> findById(long transactionId) {
        return jdbc.sql(SELECT_COLUMNS + " WHERE id = :id")
                .param("id", transactionId)
                .query(JdbcTransactionRepository::mapTransaction)
                .optional();
    }

    @Override
    public Optional<BankTransaction> findByRequestId(String requestId) {
        return jdbc.sql(SELECT_COLUMNS + " WHERE request_id = :requestId")
                .param("requestId", requestId)
                .query(JdbcTransactionRepository::mapTransaction)
                .optional();
    }

    @Override
    public void updateStatus(long transactionId, TransactionStatus status) {
        int updated = jdbc.sql("UPDATE transactions SET status = :status, updated_at = now() WHERE id = :id")
                .param("id", transactionId)
                .param("status", status.name())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Transaction " + transactionId + " does not exist");
        }
    }

    private static BankTransaction mapTransaction(ResultSet rs, int rowNum) throws SQLException {
        return new BankTransaction(
                rs.getLong("id"),
                rs.getString("request_id"),
                rs.getObject("initiated_by_user_id", Long.class),
                TransactionType.valueOf(rs.getString("transaction_type")),
                TransactionStatus.valueOf(rs.getString("status")),
                rs.getLong("from_account_id"),
                rs.getLong("to_account_id"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
