package io.github.zhlzxa.corebanking.fps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.jayway.jsonpath.JsonPath;
import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestJwts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * FPS payments end to end against the simulated network, whose behaviour is selected by the
 * creditor account: {@code REJECT...}, {@code TIMEOUT...} (accepted, answer lost) and {@code
 * LOST...} (first request lost).
 */
class FpsPaymentIT extends AbstractIntegrationIT {

    private static final long ALICE = 100;

    @MockitoSpyBean
    private FpsClient fpsClient;

    @Autowired
    private FpsReconciler reconciler;

    @Autowired
    private FpsPaymentRepository paymentRepository;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private JdbcClient jdbc;

    private long clearing;

    @BeforeEach
    void seed() {
        long alice = data.createCustomer("alice-sub");
        data.createCustomer("bob-sub");
        data.createAccount(ALICE, alice, "HKD", "1000.00");
        clearing = data.createInternalAccount("FPS-CLEARING-HKD", "HKD");
    }

    @Test
    void acceptedPaymentCompletesImmediately() {
        MvcTestResult result = pay("fps-1", "123456789", "100.00");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");
        assertThat(result).bodyJson().extractingPath("$.externalStatus").isEqualTo("ACCEPTED");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("900.00");
        assertThat(data.balanceOf(clearing)).isEqualByComparingTo("100.00");
        assertThat(data.count("audit_events WHERE action IN ('FPS_PAYMENT_SUBMITTED', 'FPS_PAYMENT_CONFIRMED')"))
                .isEqualTo(2);
        assertThat(data.unbalancedTransactions()).isZero();
    }

    @Test
    void rejectedPaymentIsReversedWithNewEntriesAndReleasesTheLimit() {
        data.setLimits(ALICE, null, "500.00");

        MvcTestResult result = pay("fps-1", "REJECT-1", "100.00");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("REVERSED");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("1000.00");
        assertThat(data.balanceOf(clearing)).isEqualByComparingTo("0.00");
        assertThat(data.count(
                        "transactions WHERE transaction_type = 'REVERSAL' AND original_transaction_id IS NOT NULL"))
                .isEqualTo(1);
        assertThat(data.count("ledger_entries")).isEqualTo(4);
        assertThat(jdbc.sql("SELECT used_amount FROM daily_transfer_usage")
                        .query(java.math.BigDecimal.class)
                        .single())
                .isEqualByComparingTo("0");
        assertThat(data.unbalancedTransactions()).isZero();
    }

    @Test
    void aTimeoutNeverRefundsAndReconciliationResolvesIt() {
        MvcTestResult result = pay("fps-1", "TIMEOUT-1", "100.00");

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PROCESSING");
        assertThat(result).bodyJson().extractingPath("$.externalStatus").isEqualTo("UNKNOWN");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("900.00");
        assertThat(data.count("outbox_events")).isZero();

        makeAllDue();
        assertThat(reconciler.reconcileDuePayments()).isEqualTo(1);

        assertThat(statusOf(idOf(result))).isEqualTo("COMPLETED");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("900.00");
        assertThat(data.count("outbox_events WHERE aggregate_id = '" + idOf(result) + "'"))
                .isEqualTo(1);
    }

    @Test
    void aPaymentFpsNeverReceivedIsResentWithTheSameEndToEndId() {
        MvcTestResult result = pay("fps-1", "LOST-1", "100.00");
        String endToEndId = JsonPath.read(body(result), "$.endToEndId");

        makeAllDue();
        reconciler.reconcileDuePayments();

        assertThat(statusOf(idOf(result))).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT end_to_end_id FROM transactions WHERE id = :id")
                        .param("id", idOf(result))
                        .query(String.class)
                        .single())
                .isEqualTo(endToEndId);
    }

    @Test
    void anUnresolvablePaymentIsEscalatedWithoutMovingMoney() {
        MvcTestResult result = pay("fps-1", "TIMEOUT-1", "100.00");
        jdbc.sql("UPDATE transactions SET attempt_count = 20").update();

        makeAllDue();
        reconciler.reconcileDuePayments();

        assertThat(statusOf(idOf(result))).isEqualTo("NEEDS_INVESTIGATION");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("900.00");
        assertThat(data.count("outbox_events")).isZero();
        assertThat(data.count("audit_events WHERE action = 'FPS_PAYMENT_NEEDS_INVESTIGATION'"))
                .isEqualTo(1);
    }

    @Test
    void aRepeatedRequestReturnsTheSamePayment() {
        long first = idOf(pay("fps-1", "123456789", "100.00"));
        MvcTestResult retry = pay("fps-1", "123456789", "100.00");

        assertThat(idOf(retry)).isEqualTo(first);
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("900.00");
        assertThat(data.count("transactions WHERE transaction_type = 'FPS_PAYMENT'"))
                .isEqualTo(1);
    }

    @Test
    void aPaymentTheAccountCannotCoverLeavesNoTrace() {
        MvcTestResult result = pay("fps-1", "123456789", "1000.01");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(data.count("transactions")).isZero();
        assertThat(data.count("audit_events WHERE action = 'FPS_PAYMENT_REJECTED'"))
                .isEqualTo(1);
    }

    @Test
    void aCurrencyWithoutClearingAccountIsNotSupported() {
        MvcTestResult result = mvc.post()
                .uri("/fps/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token("alice-sub", "bank.transfer"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "fps-usd", "fromAccountId": 100, "creditorBankCode": "004",
                         "creditorAccount": "123", "amount": "10.00", "currency": "USD"}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("CURRENCY_NOT_SUPPORTED");
    }

    @Test
    void fpsIsNeverCalledInsideADatabaseTransaction() {
        AtomicBoolean calledInTransaction = new AtomicBoolean();
        doAnswer(invocation -> {
                    calledInTransaction.compareAndSet(
                            false, TransactionSynchronizationManager.isActualTransactionActive());
                    return invocation.callRealMethod();
                })
                .when(fpsClient)
                .send(any());

        pay("fps-1", "LOST-1", "100.00");
        makeAllDue();
        reconciler.reconcileDuePayments();

        assertThat(calledInTransaction).isFalse();
    }

    @Test
    void concurrentReconcilersClaimDisjointPayments() throws Exception {
        for (int i = 0; i < 20; i++) {
            pay("fps-" + i, "TIMEOUT-" + i, "10.00");
        }
        makeAllDue();
        Instant now = Instant.now();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<List<Long>>> claims = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                claims.add(executor.submit(() -> {
                    start.await();
                    return paymentRepository.claimDue(now, now.plusSeconds(120), 15);
                }));
            }
            start.countDown();

            List<Long> first = claims.get(0).get(30, TimeUnit.SECONDS);
            List<Long> second = claims.get(1).get(30, TimeUnit.SECONDS);
            Set<Long> overlap = new HashSet<>(first);
            overlap.retainAll(second);
            assertThat(overlap).isEmpty();
            assertThat(first.size() + second.size()).isEqualTo(20);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void onlyThePayerCanReadThePayment() {
        long id = idOf(pay("fps-1", "123456789", "100.00"));

        assertThat(get(id, "alice-sub")).hasStatus(HttpStatus.OK);
        MvcTestResult other = get(id, "bob-sub");
        assertThat(other).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other).bodyJson().extractingPath("$.code").isEqualTo("PAYMENT_NOT_FOUND");
    }

    private MvcTestResult pay(String requestId, String creditorAccount, String amount) {
        return mvc.post()
                .uri("/fps/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token("alice-sub", "bank.transfer"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "fromAccountId": %d, "creditorBankCode": "004",
                         "creditorAccount": "%s", "amount": "%s", "currency": "HKD"}
                        """.formatted(requestId, ALICE, creditorAccount, amount))
                .exchange();
    }

    private MvcTestResult get(long id, String subject) {
        return mvc.get()
                .uri("/fps/payments/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token(subject, "bank.accounts.read"))
                .exchange();
    }

    private void makeAllDue() {
        jdbc.sql("UPDATE transactions SET next_attempt_at = now() - interval '1 second' WHERE status = 'PROCESSING'")
                .update();
    }

    private String statusOf(long id) {
        return jdbc.sql("SELECT status FROM transactions WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray());
    }

    private static long idOf(MvcTestResult result) {
        Number id = JsonPath.read(body(result), "$.transactionId");
        return id.longValue();
    }
}
