package io.github.zhlzxa.corebanking.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zhlzxa.corebanking.posting.InsufficientBalanceException;
import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestAuditContexts;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestSecurityContexts;
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
import org.springframework.security.concurrent.DelegatingSecurityContextCallable;

/**
 * Runs genuinely concurrent transfers against PostgreSQL. All tasks are released at the same instant
 * by a latch so that they contend for the same rows.
 */
class TransferConcurrencyIT extends AbstractIntegrationIT {

    private static final int THREADS = 8;

    @Autowired
    private TransferService transferService;

    @Autowired
    private TestDataFactory data;

    private ExecutorService executor;
    private long alice;
    private long bob;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(THREADS);
        alice = data.createCustomer("alice-sub");
        bob = data.createCustomer("bob-sub");
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void oppositeTransfersBetweenTheSameAccountsNeverDeadlock() throws Exception {
        data.createAccount(1, alice, "HKD", "10000");
        data.createAccount(2, bob, "HKD", "10000");
        List<Callable<BankTransaction>> tasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            boolean forward = i % 2 == 0;
            tasks.add(forward ? transfer(alice, "req-" + i, 1, 2, "10.00") : transfer(bob, "req-" + i, 2, 1, "10.00"));
        }

        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(data.balanceOf(1)).isEqualByComparingTo("10000");
        assertThat(data.balanceOf(2)).isEqualByComparingTo("10000");
        assertThat(data.unbalancedTransactions()).isZero();
    }

    @Test
    void concurrentDebitsNeverOverdrawTheSourceAccount() throws Exception {
        data.createAccount(1, alice, "HKD", "100.00");
        data.createAccount(2, bob, "HKD", "0.00");
        List<Callable<BankTransaction>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(transfer(alice, "req-" + i, 1, 2, "10.00"));
        }

        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(10);
        assertThat(outcomes.stream().filter(o -> o.failure() instanceof InsufficientBalanceException))
                .hasSize(10);
        assertThat(data.balanceOf(1)).isEqualByComparingTo("0.00");
        assertThat(data.balanceOf(2)).isEqualByComparingTo("100.00");
        assertThat(data.unbalancedTransactions()).isZero();
    }

    @Test
    void concurrentDuplicatesOfOneRequestMoveMoneyOnce() throws Exception {
        data.createAccount(1, alice, "HKD", "1000.00");
        data.createAccount(2, bob, "HKD", "0.00");
        List<Callable<BankTransaction>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(transfer(alice, "req-same", 1, 2, "250.00"));
        }

        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(outcomes.stream().map(o -> o.transaction().id()).distinct()).hasSize(1);
        assertThat(data.balanceOf(1)).isEqualByComparingTo("750.00");
        assertThat(data.count("transactions")).isEqualTo(1);
    }

    /** A transfer task that runs with the given customer's security context on a worker thread. */
    private Callable<BankTransaction> transfer(long customer, String requestId, long from, long to, String amount) {
        TransferCommand command = new TransferCommand(customer, requestId, from, to, new BigDecimal(amount), "HKD");
        return new DelegatingSecurityContextCallable<>(
                () -> transferService.transfer(TestAuditContexts.customer(customer), command),
                TestSecurityContexts.customer(customer, "bank.transfer"));
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

    private record Outcome(BankTransaction transaction, Throwable failure) {

        boolean succeeded() {
            return failure == null;
        }
    }
}
