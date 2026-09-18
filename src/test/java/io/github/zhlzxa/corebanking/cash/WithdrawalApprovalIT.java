package io.github.zhlzxa.corebanking.cash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestJwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The four-eyes approval of large cash withdrawals, end to end. */
class WithdrawalApprovalIT extends AbstractIntegrationIT {

    private static final long ALICE = 100;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private JdbcClient jdbc;

    private long maker;
    private long checker;
    private final String makerToken = teller("maker-sub");
    private final String checkerToken = teller("checker-sub");

    @BeforeEach
    void seed() {
        long alice = data.createCustomer("alice-sub");
        maker = data.createUser("maker-sub", "TELLER", "ACTIVE");
        checker = data.createUser("checker-sub", "TELLER", "ACTIVE");
        data.createAccount(ALICE, alice, "HKD", "100000.00");
        data.createInternalAccount("CASH-HKD", "HKD");
    }

    @Test
    void largeWithdrawalWaitsForApprovalWithoutMovingMoney() {
        MvcTestResult result = withdraw("wd-1", "60000.00");

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).matches("/teller/approvals/\\d+");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("100000.00");
        assertThat(data.count("transactions")).isZero();
        assertThat(data.count("audit_events WHERE action = 'WITHDRAWAL_APPROVAL_REQUESTED'"))
                .isEqualTo(1);
    }

    @Test
    void theMakerCannotApproveTheirOwnRequest() {
        long approval = approvalIdOf(withdraw("wd-1", "60000.00"));

        MvcTestResult result = decide(makerToken, approval, "approve");

        assertProblem(result, HttpStatus.FORBIDDEN, "FOUR_EYES_REQUIRED");
        assertThat(statusOf(approval)).isEqualTo("PENDING");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("100000.00");
    }

    @Test
    void anotherTellersApprovalPaysOutTheCash() {
        long approval = approvalIdOf(withdraw("wd-1", "60000.00"));

        MvcTestResult result = decide(checkerToken, approval, "approve");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("WITHDRAWAL");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("40000.00");
        assertThat(statusOf(approval)).isEqualTo("EXECUTED");
        assertThat(jdbc.sql("""
                                SELECT t.initiated_by_user_id = :maker AND a.checker_user_id = :checker
                                       AND a.transaction_id = t.id
                                FROM withdrawal_approvals a JOIN transactions t ON t.request_id = a.request_id
                                """)
                        .param("maker", maker)
                        .param("checker", checker)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(jdbc.sql("SELECT actor_user_id FROM audit_events WHERE action = 'CASH_WITHDRAWAL_COMPLETED'")
                        .query(Long.class)
                        .single())
                .isEqualTo(checker);
        assertThat(data.unbalancedTransactions()).isZero();
    }

    @Test
    void aDecisionIsFinal() {
        long approval = approvalIdOf(withdraw("wd-1", "60000.00"));
        decide(checkerToken, approval, "approve");

        assertProblem(decide(checkerToken, approval, "approve"), HttpStatus.CONFLICT, "APPROVAL_NOT_PENDING");
        assertProblem(decide(checkerToken, approval, "reject"), HttpStatus.CONFLICT, "APPROVAL_NOT_PENDING");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("40000.00");
    }

    @Test
    void aRejectedRequestMovesNoMoney() {
        long approval = approvalIdOf(withdraw("wd-1", "60000.00"));

        MvcTestResult result = decide(checkerToken, approval, "reject");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("REJECTED");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("100000.00");
        assertThat(data.count("transactions")).isZero();
    }

    @Test
    void anExpiredRequestCannotBeApprovedAndIsMarkedExpired() {
        long approval = approvalIdOf(withdraw("wd-1", "60000.00"));
        jdbc.sql("UPDATE withdrawal_approvals SET expires_at = now() - interval '1 minute' WHERE id = :id")
                .param("id", approval)
                .update();

        MvcTestResult result = decide(checkerToken, approval, "approve");

        assertProblem(result, HttpStatus.CONFLICT, "APPROVAL_EXPIRED");
        assertThat(statusOf(approval)).isEqualTo("EXPIRED");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("100000.00");
        assertThat(data.count("audit_events WHERE action = 'WITHDRAWAL_APPROVAL_EXPIRED'"))
                .isEqualTo(1);
    }

    @Test
    void aRepeatedRequestReturnsTheSameApproval() {
        long first = approvalIdOf(withdraw("wd-1", "60000.00"));
        long second = approvalIdOf(withdraw("wd-1", "60000.00"));

        assertThat(second).isEqualTo(first);
        assertThat(data.count("withdrawal_approvals")).isEqualTo(1);
    }

    @Test
    void aRequestThatCouldNotBePaidOutIsRejectedImmediately() {
        MvcTestResult result = withdraw("wd-1", "150000.00");

        assertProblem(result, HttpStatus.CONFLICT, "INSUFFICIENT_BALANCE");
        assertThat(data.count("withdrawal_approvals")).isZero();
    }

    @Test
    void theDatabaseRefusesAMakerAsChecker() {
        long approval = approvalIdOf(withdraw("wd-1", "60000.00"));

        assertThatThrownBy(() -> jdbc.sql("""
                                UPDATE withdrawal_approvals
                                SET status = 'REJECTED', checker_user_id = maker_user_id, decided_at = now()
                                WHERE id = :id
                                """).param("id", approval).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_withdrawal_approvals_four_eyes");
    }

    private static String teller(String subject) {
        return TestJwts.builder(subject)
                .scopes("bank.cash")
                .claim("branch_code", "BR-001")
                .build();
    }

    private MvcTestResult withdraw(String requestId, String amount) {
        return mvc.post()
                .uri("/teller/withdrawals")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + makerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "accountId": %d, "amount": "%s", "currency": "HKD"}
                        """.formatted(requestId, ALICE, amount))
                .exchange();
    }

    private MvcTestResult decide(String token, long approvalId, String decision) {
        return mvc.post()
                .uri("/teller/approvals/" + approvalId + "/" + decision)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
    }

    private String statusOf(long approvalId) {
        return jdbc.sql("SELECT status FROM withdrawal_approvals WHERE id = :id")
                .param("id", approvalId)
                .query(String.class)
                .single();
    }

    private static long approvalIdOf(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        Number id = JsonPath.read(new String(result.getResponse().getContentAsByteArray()), "$.approvalId");
        return id.longValue();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
