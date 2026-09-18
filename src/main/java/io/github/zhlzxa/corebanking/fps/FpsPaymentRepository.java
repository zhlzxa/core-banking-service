package io.github.zhlzxa.corebanking.fps;

import io.github.zhlzxa.corebanking.fps.FpsPayment.ExternalStatus;
import io.github.zhlzxa.corebanking.transaction.TransactionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.JdbcUpdateAffectedIncorrectNumberOfRowsException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** FPS-specific state of payment transactions. */
@Repository
class FpsPaymentRepository {

    private static final String COLUMNS = """
            SELECT id, request_id, initiated_by_user_id, from_account_id, to_account_id, amount, currency,
                   status, external_status, end_to_end_id, creditor_bank_code, creditor_account,
                   attempt_count, last_error_code, created_at
            FROM transactions
            WHERE transaction_type = 'FPS_PAYMENT'
            """;

    private final JdbcClient jdbc;

    FpsPaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<FpsPayment> findById(long id) {
        return jdbc.sql(COLUMNS + " AND id = :id")
                .param("id", id)
                .query(FpsPaymentRepository::map)
                .optional();
    }

    Optional<FpsPayment> findByIdForUpdate(long id) {
        return jdbc.sql(COLUMNS + " AND id = :id FOR UPDATE")
                .param("id", id)
                .query(FpsPaymentRepository::map)
                .optional();
    }

    /**
     * Records the destination and end-to-end id of a newly posted payment and makes it due for
     * reconciliation at {@code reconcileFrom}, which acts as a safety net if the process stops before
     * the payment is sent.
     */
    void attachPaymentDetails(
            long id, String endToEndId, String creditorBankCode, String creditorAccount, Instant reconcileFrom) {
        int updated = jdbc.sql("""
                        UPDATE transactions
                        SET end_to_end_id = :endToEndId, creditor_bank_code = :bankCode,
                            creditor_account = :account, external_status = 'NOT_SENT',
                            next_attempt_at = :next, updated_at = now()
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("endToEndId", endToEndId)
                .param("bankCode", creditorBankCode)
                .param("account", creditorAccount)
                .param("next", reconcileFrom.atOffset(ZoneOffset.UTC))
                .update();
        if (updated != 1) {
            throw new JdbcUpdateAffectedIncorrectNumberOfRowsException("attach FPS details to " + id, 1, updated);
        }
    }

    void recordOutcome(
            long id,
            TransactionStatus status,
            ExternalStatus externalStatus,
            @Nullable Instant nextAttemptAt,
            @Nullable String errorCode) {
        int updated = jdbc.sql("""
                        UPDATE transactions
                        SET status = :status, external_status = :external, next_attempt_at = :next,
                            last_error_code = :error, updated_at = now()
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("status", status.name())
                .param("external", externalStatus.name())
                .param("next", nextAttemptAt == null ? null : nextAttemptAt.atOffset(ZoneOffset.UTC))
                .param("error", errorCode)
                .update();
        if (updated != 1) {
            throw new JdbcUpdateAffectedIncorrectNumberOfRowsException("record FPS outcome of " + id, 1, updated);
        }
    }

    /**
     * Claims up to {@code limit} unresolved payments that are due, for exclusive processing until
     * {@code leaseUntil}.
     *
     * <p>A single statement selects the rows with {@code FOR UPDATE SKIP LOCKED} and moves their next
     * attempt to the end of the lease. Concurrent reconcilers therefore receive disjoint rows, and
     * because the lease is committed, a payment is not picked up again while it is being worked on,
     * even though no lock is held during the network calls that follow. If a reconciler dies, the
     * lease expires and the payment becomes due again.
     */
    List<Long> claimDue(Instant now, Instant leaseUntil, int limit) {
        return jdbc.sql("""
                        UPDATE transactions
                        SET next_attempt_at = :leaseUntil, attempt_count = attempt_count + 1, updated_at = now()
                        WHERE id IN (
                            SELECT id FROM transactions
                            WHERE status = 'PROCESSING' AND next_attempt_at <= :now
                            ORDER BY next_attempt_at
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED)
                        RETURNING id
                        """)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("leaseUntil", leaseUntil.atOffset(ZoneOffset.UTC))
                .param("limit", limit)
                .query(Long.class)
                .list();
    }

    void linkReversal(long reversalId, long originalId) {
        int updated = jdbc.sql("UPDATE transactions SET original_transaction_id = :original WHERE id = :id")
                .param("id", reversalId)
                .param("original", originalId)
                .update();
        if (updated != 1) {
            throw new JdbcUpdateAffectedIncorrectNumberOfRowsException("link reversal " + reversalId, 1, updated);
        }
    }

    private static FpsPayment map(ResultSet rs, int rowNum) throws SQLException {
        return new FpsPayment(
                rs.getLong("id"),
                rs.getString("request_id"),
                rs.getObject("initiated_by_user_id", Long.class),
                rs.getLong("from_account_id"),
                rs.getLong("to_account_id"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                TransactionStatus.valueOf(rs.getString("status")),
                ExternalStatus.valueOf(rs.getString("external_status")),
                rs.getString("end_to_end_id"),
                rs.getString("creditor_bank_code"),
                rs.getString("creditor_account"),
                rs.getInt("attempt_count"),
                rs.getString("last_error_code"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
