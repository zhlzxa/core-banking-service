package io.github.zhlzxa.corebanking.transfer.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestJwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Account status, currency precision and per-transaction limits, verified through the API. */
class AccountRulesIT extends AbstractIntegrationIT {

    private static final long ALICE = 100;
    private static final long BOB = 200;
    private static final long ALICE_JPY = 101;
    private static final long BOB_JPY = 201;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @BeforeEach
    void seed() {
        long alice = data.createCustomer("alice-sub");
        long bob = data.createCustomer("bob-sub");
        data.createAccount(ALICE, alice, "HKD", "1000.00");
        data.createAccount(BOB, bob, "HKD", "500.00");
        data.createAccount(ALICE_JPY, alice, "JPY", "100000");
        data.createAccount(BOB_JPY, bob, "JPY", "0");
    }

    @Test
    void frozenAccountCannotSendButCanStillReceive() {
        data.setStatus(ALICE, "FROZEN", "COURT_ORDER");

        assertProblem(
                transfer("alice-sub", "r1", ALICE, BOB, "10.00", "HKD"),
                HttpStatus.CONFLICT,
                "SOURCE_ACCOUNT_NOT_ACTIVE");
        assertThat(transfer("bob-sub", "r2", BOB, ALICE, "10.00", "HKD")).hasStatus(HttpStatus.CREATED);
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("1010.00");
    }

    @Test
    void closedAccountCanNeitherSendNorReceive() {
        data.setStatus(BOB, "CLOSED", null);

        assertProblem(
                transfer("alice-sub", "r1", ALICE, BOB, "10.00", "HKD"),
                HttpStatus.CONFLICT,
                "DESTINATION_ACCOUNT_CLOSED");
        assertProblem(
                transfer("bob-sub", "r2", BOB, ALICE, "10.00", "HKD"),
                HttpStatus.CONFLICT,
                "SOURCE_ACCOUNT_NOT_ACTIVE");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("1000.00");
        assertThat(data.balanceOf(BOB)).isEqualByComparingTo("500.00");
    }

    @Test
    void wholeYenAreAcceptedButFractionalYenAreNot() {
        assertThat(transfer("alice-sub", "r1", ALICE_JPY, BOB_JPY, "500", "JPY"))
                .hasStatus(HttpStatus.CREATED);
        assertProblem(
                transfer("alice-sub", "r2", ALICE_JPY, BOB_JPY, "500.5", "JPY"),
                HttpStatus.BAD_REQUEST,
                "INVALID_AMOUNT_SCALE");
        assertThat(data.balanceOf(BOB_JPY)).isEqualByComparingTo("500");
    }

    @Test
    void perTransactionLimitIsInclusive() {
        data.setLimits(ALICE, "100.00", null);

        assertThat(transfer("alice-sub", "r1", ALICE, BOB, "100.00", "HKD")).hasStatus(HttpStatus.CREATED);
        assertProblem(
                transfer("alice-sub", "r2", ALICE, BOB, "100.01", "HKD"),
                HttpStatus.CONFLICT,
                "TRANSFER_LIMIT_EXCEEDED");
    }

    @Test
    void ruleViolationsAreAuditedAsRejections() {
        data.setStatus(ALICE, "FROZEN", "FRAUD_SUSPECTED");

        transfer("alice-sub", "r1", ALICE, BOB, "10.00", "HKD");

        assertThat(data.count("audit_events WHERE action = 'TRANSFER_REJECTED' "
                        + "AND reason_code = 'SOURCE_ACCOUNT_NOT_ACTIVE'"))
                .isEqualTo(1);
    }

    private MvcTestResult transfer(String subject, String requestId, long from, long to, String amount, String ccy) {
        return mvc.post()
                .uri("/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token(subject, "bank.transfer"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "fromAccountId": %d, "toAccountId": %d,
                         "amount": "%s", "currency": "%s"}
                        """.formatted(requestId, from, to, amount, ccy))
                .exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
