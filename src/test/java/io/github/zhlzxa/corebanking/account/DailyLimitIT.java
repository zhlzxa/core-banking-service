package io.github.zhlzxa.corebanking.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.github.zhlzxa.corebanking.ledger.LedgerRepository;
import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestJwts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

class DailyLimitIT extends AbstractIntegrationIT {

    private static final long ALICE = 100;
    private static final long BOB = 200;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);

    @MockitoSpyBean
    private LedgerRepository ledgerRepository;

    @Autowired
    private DailyTransferUsageRepository usageRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seed() {
        long alice = data.createCustomer("alice-sub");
        long bob = data.createCustomer("bob-sub");
        data.createAccount(ALICE, alice, "HKD", "100000.00");
        data.createAccount(BOB, bob, "HKD", "0.00");
    }

    @Test
    void firstTransferOfTheDayAboveTheLimitIsRejectedToo() {
        boolean consumed = consume("60000", "50000");

        assertThat(consumed).isFalse();
        assertThat(data.count("daily_transfer_usage")).isZero();
    }

    @Test
    void concurrentConsumersCannotTogetherExceedTheLimit() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 20; round++) {
                jdbc.sql("TRUNCATE daily_transfer_usage").update();
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Boolean>> results = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    results.add(executor.submit(awaiting(start, () -> consume("30000", "50000"))));
                }
                start.countDown();

                int successes = 0;
                for (Future<Boolean> result : results) {
                    successes += result.get(30, TimeUnit.SECONDS) ? 1 : 0;
                }
                assertThat(successes).as("round %d", round).isEqualTo(1);
                assertThat(usedOn(DAY)).isEqualByComparingTo("30000");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void transfersConsumeTheLimitUntilItIsExhausted() {
        data.setLimits(ALICE, null, "150.00");

        assertThat(transfer("r1", "100.00")).hasStatus(HttpStatus.CREATED);
        assertLimitExceeded(transfer("r2", "60.00"));
        assertThat(transfer("r3", "50.00")).hasStatus(HttpStatus.CREATED);
        assertLimitExceeded(transfer("r4", "0.01"));

        assertThat(jdbc.sql("SELECT used_amount FROM daily_transfer_usage WHERE account_id = :id")
                        .param("id", ALICE)
                        .query(BigDecimal.class)
                        .single())
                .isEqualByComparingTo("150.00");
    }

    @Test
    void aTransferThatRollsBackReleasesItsShareOfTheLimit() {
        data.setLimits(ALICE, null, "150.00");
        doThrow(new DataAccessResourceFailureException("Simulated storage failure"))
                .when(ledgerRepository)
                .append(any());

        assertThat(transfer("r1", "100.00")).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(data.count("daily_transfer_usage")).isZero();
    }

    private boolean consume(String amount, String limit) {
        return transactionTemplate.execute(
                status -> usageRepository.tryConsume(ALICE, DAY, new BigDecimal(amount), new BigDecimal(limit)));
    }

    private BigDecimal usedOn(LocalDate day) {
        return jdbc.sql("SELECT used_amount FROM daily_transfer_usage WHERE account_id = :id AND usage_date = :day")
                .param("id", ALICE)
                .param("day", day)
                .query(BigDecimal.class)
                .single();
    }

    private static <T> Callable<T> awaiting(CountDownLatch start, Callable<T> task) {
        return () -> {
            start.await();
            return task.call();
        };
    }

    private MvcTestResult transfer(String requestId, String amount) {
        return mvc.post()
                .uri("/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token("alice-sub", "bank.transfer"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "fromAccountId": 100, "toAccountId": 200,
                         "amount": "%s", "currency": "HKD"}
                        """.formatted(requestId, amount))
                .exchange();
    }

    private static void assertLimitExceeded(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("TRANSFER_LIMIT_EXCEEDED");
    }
}
