package io.github.zhlzxa.corebanking.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Runs genuinely concurrent transfers against PostgreSQL. All tasks are released at the same instant
 * by a latch so that they contend for the same rows.
 */
class TransferConcurrencyIT extends AbstractIntegrationIT {

    private static final int THREADS = 8;

    @Autowired
    private TransferService transferService;

    @Autowired
    private JdbcClient jdbc;

    private ExecutorService executor;

    @BeforeEach
    void startExecutor() {
        executor = Executors.newFixedThreadPool(THREADS);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void oppositeTransfersBetweenTheSameAccountsNeverDeadlock() throws Exception {
        seedAccount(1, "10000");
        seedAccount(2, "10000");
        List<Callable<BankTransaction>> tasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            boolean forward = i % 2 == 0;
            tasks.add(transfer("req-" + i, forward ? 1 : 2, forward ? 2 : 1, "10.00"));
        }

        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(balanceOf(1)).isEqualByComparingTo("10000");
        assertThat(balanceOf(2)).isEqualByComparingTo("10000");
        assertLedgerIsBalanced();
    }

    @Test
    void concurrentDebitsNeverOverdrawTheSourceAccount() throws Exception {
        seedAccount(1, "100.00");
        seedAccount(2, "0.00");
        List<Callable<BankTransaction>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(transfer("req-" + i, 1, 2, "10.00"));
        }

        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(10);
        assertThat(outcomes.stream().filter(o -> o.failure() instanceof InsufficientBalanceException))
                .hasSize(10);
        assertThat(balanceOf(1)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(2)).isEqualByComparingTo("100.00");
        assertLedgerIsBalanced();
    }

    @Test
    void concurrentDuplicatesOfOneRequestMoveMoneyOnce() throws Exception {
        seedAccount(1, "1000.00");
        seedAccount(2, "0.00");
        List<Callable<BankTransaction>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(transfer("req-same", 1, 2, "250.00"));
        }

        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(outcomes.stream().map(o -> o.transaction().id()).distinct()).hasSize(1);
        assertThat(balanceOf(1)).isEqualByComparingTo("750.00");
        assertThat(jdbc.sql("SELECT count(*) FROM transactions")
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    private Callable<BankTransaction> transfer(String requestId, long from, long to, String amount) {
        return () -> transferService.transfer(new TransferCommand(requestId, from, to, new BigDecimal(amount), "HKD"));
    }

    private List<Outcome> runConcurrently(List<Callable<BankTransaction>> tasks) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<BankTransaction>> futures = new ArrayList<>();
        for (Callable<BankTransaction> task : tasks) {
            futures.add(executor.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();

        List<Outcome> outcomes = new ArrayList<>();
        for (Future<BankTransaction> future : futures) {
            try {
                outcomes.add(new Outcome(future.get(60, TimeUnit.SECONDS), null));
            } catch (ExecutionException e) {
                outcomes.add(new Outcome(null, e.getCause()));
            } catch (TimeoutException e) {
                throw new AssertionError("Transfer did not finish in time; possible deadlock", e);
            }
        }
        return outcomes;
    }

    private void seedAccount(long id, String balance) {
        jdbc.sql("INSERT INTO accounts (id, currency, balance) VALUES (:id, 'HKD', :balance)")
                .param("id", id)
                .param("balance", new BigDecimal(balance))
                .update();
    }

    private BigDecimal balanceOf(long accountId) {
        return jdbc.sql("SELECT balance FROM accounts WHERE id = :id")
                .param("id", accountId)
                .query(BigDecimal.class)
                .single();
    }

    private void assertLedgerIsBalanced() {
        Integer unbalanced = jdbc.sql("""
                        SELECT count(*) FROM (
                            SELECT transaction_id
                            FROM ledger_entries
                            GROUP BY transaction_id
                            HAVING sum(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END) <> 0
                        ) unbalanced
                        """).query(Integer.class).single();
        assertThat(unbalanced).isZero();
    }

    private record Outcome(BankTransaction transaction, Throwable failure) {

        boolean succeeded() {
            return failure == null;
        }
    }
}
