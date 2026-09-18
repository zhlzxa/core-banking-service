package io.github.zhlzxa.corebanking.transfer.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class TransferApiIT extends AbstractIntegrationIT {

    private static final long ALICE = 100;
    private static final long BOB = 200;
    private static final long CAROL_USD = 300;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seedAccounts() {
        jdbc.sql("""
                        INSERT INTO accounts (id, currency, balance)
                        VALUES (100, 'HKD', 1000.00), (200, 'HKD', 500.00), (300, 'USD', 50.00)
                        """).update();
    }

    @Test
    void completesTransferAndPostsBalancedLedger() {
        MvcTestResult result = postTransfer("req-001", ALICE, BOB, "100.00", "HKD");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");
        assertThat(result).bodyJson().extractingPath("$.amount").isEqualTo("100.00");
        assertThat(result).bodyJson().extractingPath("$.requestId").isEqualTo("req-001");
        assertThat(balanceOf(ALICE)).isEqualByComparingTo("900.00");
        assertThat(balanceOf(BOB)).isEqualByComparingTo("600.00");
        assertThat(count("SELECT count(*) FROM ledger_entries")).isEqualTo(2);
        assertThat(count("""
                        SELECT count(*) FROM (
                            SELECT transaction_id
                            FROM ledger_entries
                            GROUP BY transaction_id
                            HAVING sum(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END) <> 0
                        ) unbalanced
                        """)).isZero();
    }

    @Test
    void retryWithSameRequestIdMovesMoneyOnlyOnce() throws Exception {
        MvcTestResult first = postTransfer("req-retry", ALICE, BOB, "100.00", "HKD");
        MvcTestResult retry = postTransfer("req-retry", ALICE, BOB, "100.00", "HKD");

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(retry).hasStatus(HttpStatus.CREATED);
        assertThat(retry).bodyJson().extractingPath("$.transactionId").isEqualTo(transactionIdOf(first));
        assertThat(balanceOf(ALICE)).isEqualByComparingTo("900.00");
        assertThat(count("SELECT count(*) FROM transactions")).isEqualTo(1);
    }

    @Test
    void reusingRequestIdForDifferentInstructionIsRejected() {
        postTransfer("req-reuse", ALICE, BOB, "100.00", "HKD");

        MvcTestResult result = postTransfer("req-reuse", ALICE, BOB, "250.00", "HKD");

        assertProblem(result, HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
        assertThat(balanceOf(ALICE)).isEqualByComparingTo("900.00");
    }

    @Test
    void insufficientBalanceLeavesNoTrace() {
        MvcTestResult result = postTransfer("req-big", ALICE, BOB, "1000.01", "HKD");

        assertProblem(result, HttpStatus.CONFLICT, "INSUFFICIENT_BALANCE");
        assertThat(balanceOf(ALICE)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(BOB)).isEqualByComparingTo("500.00");
        assertThat(count("SELECT count(*) FROM transactions")).isZero();
        assertThat(count("SELECT count(*) FROM ledger_entries")).isZero();
    }

    @Test
    void rejectedRequestCanBeRetriedWithTheSameKeyOnceFunded() {
        postTransfer("req-later", ALICE, BOB, "1200.00", "HKD");
        jdbc.sql("UPDATE accounts SET balance = 1500 WHERE id = :id")
                .param("id", ALICE)
                .update();

        MvcTestResult result = postTransfer("req-later", ALICE, BOB, "1200.00", "HKD");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(balanceOf(ALICE)).isEqualByComparingTo("300.00");
    }

    @Test
    void unknownAccountIsNotFound() {
        assertProblem(postTransfer("req-404", ALICE, 999, "10.00", "HKD"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
    }

    @Test
    void crossCurrencyTransferIsRejected() {
        assertProblem(
                postTransfer("req-fx", ALICE, CAROL_USD, "10.00", "HKD"), HttpStatus.CONFLICT, "CURRENCY_MISMATCH");
        assertThat(balanceOf(ALICE)).isEqualByComparingTo("1000.00");
    }

    @Test
    void transferToSelfIsInvalid() {
        assertProblem(
                postTransfer("req-self", ALICE, ALICE, "10.00", "HKD"), HttpStatus.BAD_REQUEST, "INVALID_TRANSFER");
    }

    @Test
    void invalidFieldsAreReportedWithoutEchoingInput() throws Exception {
        MvcTestResult result = mvc.post()
                .uri("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "has spaces <script>", "fromAccountId": 100, "toAccountId": 200,
                         "amount": "-5", "currency": "hkd"}
                        """)
                .exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactlyInAnyOrder("requestId", "amount", "currency");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("<script>");
        assertThat(count("SELECT count(*) FROM transactions")).isZero();
    }

    @Test
    void malformedJsonIsABadRequest() {
        MvcTestResult result = mvc.post()
                .uri("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\": ")
                .exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
    }

    @Test
    void unsupportedMethodStillReturnsAProblemWithCode() {
        MvcTestResult result = mvc.put().uri("/transfers").exchange();

        assertProblem(result, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED");
    }

    private MvcTestResult postTransfer(String requestId, long from, long to, String amount, String currency) {
        return mvc.post()
                .uri("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "fromAccountId": %d, "toAccountId": %d,
                         "amount": "%s", "currency": "%s"}
                        """.formatted(requestId, from, to, amount, currency))
                .exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static Object transactionIdOf(MvcTestResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId");
    }

    private BigDecimal balanceOf(long accountId) {
        return jdbc.sql("SELECT balance FROM accounts WHERE id = :id")
                .param("id", accountId)
                .query(BigDecimal.class)
                .single();
    }

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }
}
