package io.github.zhlzxa.corebanking.transaction;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Manual tool for inspecting query plans at a realistic data volume.
 *
 * <p>Plan shapes depend on statistics and data distribution, so they are not asserted in CI. Run
 * this class on demand after changing a query or an index, and compare the printed plans; the
 * expected shape for the history query is an index scan per branch with an early LIMIT, and no
 * sort over the account's full history.
 */
@Disabled("Manual query-plan investigation; plan shapes are not a stable CI assertion")
class QueryPlanInvestigationIT extends AbstractIntegrationIT {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TestDataFactory data;

    @Test
    void explainTransactionHistoryOverALargeTable() {
        long owner = data.createCustomer("volume-sub");
        jdbc.sql("""
                        INSERT INTO accounts (id, user_id, currency, balance)
                        SELECT g, :owner, 'HKD', 0 FROM generate_series(1, 200) g
                        """).param("owner", owner).update();
        // 200,000 transfers spread over 200 accounts and one year.
        jdbc.sql("""
                        INSERT INTO transactions
                            (request_id, transaction_type, status, from_account_id, to_account_id,
                             amount, currency, created_at)
                        SELECT 'volume-' || g, 'TRANSFER', 'COMPLETED',
                               1 + (g % 200), 1 + ((g + 1 + g / 200) % 200),
                               1, 'HKD', now() - (g || ' seconds')::interval * 150
                        FROM generate_series(1, 200000) g
                        WHERE 1 + (g % 200) <> 1 + ((g + 1 + g / 200) % 200)
                        """).update();
        jdbc.sql("ANALYZE transactions").update();

        List<String> plan = jdbc.sql("""
                        EXPLAIN (ANALYZE, BUFFERS)
                        SELECT * FROM (
                            (SELECT id, created_at FROM transactions WHERE from_account_id = 42
                             ORDER BY created_at DESC, id DESC LIMIT 51)
                            UNION ALL
                            (SELECT id, created_at FROM transactions WHERE to_account_id = 42
                             ORDER BY created_at DESC, id DESC LIMIT 51)
                        ) history ORDER BY created_at DESC, id DESC LIMIT 51
                        """).query(String.class).list();
        plan.forEach(System.out::println);
    }
}
