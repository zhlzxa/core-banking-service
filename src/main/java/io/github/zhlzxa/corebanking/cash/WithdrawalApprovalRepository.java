package io.github.zhlzxa.corebanking.cash;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.JdbcUpdateAffectedIncorrectNumberOfRowsException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Persistence of withdrawal approvals. Decisions are only ever made on a row locked for update. */
@Repository
class WithdrawalApprovalRepository {

    private static final String COLUMNS = """
            SELECT id, request_id, account_id, amount, currency, maker_user_id, maker_branch_code,
                   status, checker_user_id, transaction_id, created_at, expires_at
            FROM withdrawal_approvals
            """;

    private final JdbcClient jdbc;

    WithdrawalApprovalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records a new pending request unless its request id is already known.
     *
     * @return the new approval id, or empty if the request id was taken
     */
    Optional<Long> insertIfAbsent(CashCommand command, String makerBranchCode, Instant expiresAt) {
        return jdbc.sql("""
                        INSERT INTO withdrawal_approvals
                            (request_id, account_id, amount, currency, maker_user_id, maker_branch_code, expires_at)
                        VALUES (:requestId, :accountId, :amount, :currency, :maker, :branch, :expiresAt)
                        ON CONFLICT (request_id) DO NOTHING
                        RETURNING id
                        """)
                .param("requestId", command.requestId())
                .param("accountId", command.accountId())
                .param("amount", command.amount())
                .param("currency", command.currency())
                .param("maker", command.operatorId())
                .param("branch", makerBranchCode)
                .param("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .optional();
    }

    Optional<WithdrawalApproval> findById(long id) {
        return jdbc.sql(COLUMNS + " WHERE id = :id")
                .param("id", id)
                .query(WithdrawalApprovalRepository::map)
                .optional();
    }

    Optional<WithdrawalApproval> findByIdForUpdate(long id) {
        return jdbc.sql(COLUMNS + " WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(WithdrawalApprovalRepository::map)
                .optional();
    }

    Optional<WithdrawalApproval> findByRequestId(String requestId) {
        return jdbc.sql(COLUMNS + " WHERE request_id = :requestId")
                .param("requestId", requestId)
                .query(WithdrawalApprovalRepository::map)
                .optional();
    }

    void markExecuted(long id, long checkerUserId, long transactionId, Instant decidedAt) {
        decide(id, "EXECUTED", checkerUserId, transactionId, decidedAt);
    }

    void markRejected(long id, long checkerUserId, Instant decidedAt) {
        decide(id, "REJECTED", checkerUserId, null, decidedAt);
    }

    void markExpired(long id, Instant decidedAt) {
        decide(id, "EXPIRED", null, null, decidedAt);
    }

    /** The {@code status = 'PENDING'} predicate guarantees that a decision is made exactly once. */
    private void decide(long id, String status, Long checkerUserId, Long transactionId, Instant decidedAt) {
        int updated = jdbc.sql("""
                        UPDATE withdrawal_approvals
                        SET status = :status, checker_user_id = :checker, transaction_id = :transactionId,
                            decided_at = :decidedAt
                        WHERE id = :id AND status = 'PENDING'
                        """)
                .param("id", id)
                .param("status", status)
                .param("checker", checkerUserId)
                .param("transactionId", transactionId)
                .param("decidedAt", decidedAt.atOffset(ZoneOffset.UTC))
                .update();
        if (updated != 1) {
            throw new JdbcUpdateAffectedIncorrectNumberOfRowsException("decide withdrawal approval " + id, 1, updated);
        }
    }

    private static WithdrawalApproval map(ResultSet rs, int rowNum) throws SQLException {
        return new WithdrawalApproval(
                rs.getLong("id"),
                rs.getString("request_id"),
                rs.getLong("account_id"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getLong("maker_user_id"),
                rs.getString("maker_branch_code"),
                WithdrawalApproval.ApprovalStatus.valueOf(rs.getString("status")),
                rs.getObject("checker_user_id", Long.class),
                rs.getObject("transaction_id", Long.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant());
    }
}
