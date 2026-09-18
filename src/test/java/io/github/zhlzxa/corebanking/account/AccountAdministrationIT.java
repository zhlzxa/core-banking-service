package io.github.zhlzxa.corebanking.account;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestJwts;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class AccountAdministrationIT extends AbstractIntegrationIT {

    private static final long ALICE_ACCOUNT = 100;
    private static final long EMPTY_ACCOUNT = 101;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private JdbcClient jdbc;

    private long admin;
    private final String adminToken = TestJwts.token("admin-sub", "bank.accounts.admin");

    @BeforeEach
    void seed() {
        long alice = data.createCustomer("alice-sub");
        admin = data.createUser("admin-sub", "ADMIN", "ACTIVE");
        data.createAccount(ALICE_ACCOUNT, alice, "HKD", "1000.00");
        data.createAccount(EMPTY_ACCOUNT, alice, "HKD", "0.00");
    }

    @Test
    void freezingRecordsTheReasonAndIsAuditedWithTheAdministrator() {
        MvcTestResult result = post("/freeze", "{\"reason\": \"COURT_ORDER\"}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("FROZEN");
        assertThat(result).bodyJson().extractingPath("$.statusReason").isEqualTo("COURT_ORDER");
        Map<String, Object> event = jdbc.sql("""
                        SELECT actor_user_id, resource_id, metadata ->> 'reason' AS reason,
                               metadata ->> 'previousStatus' AS previous_status
                        FROM audit_events WHERE action = 'ACCOUNT_FROZEN'
                        """).query().singleRow();
        assertThat(event)
                .containsEntry("actor_user_id", admin)
                .containsEntry("resource_id", "100")
                .containsEntry("reason", "COURT_ORDER")
                .containsEntry("previous_status", "ACTIVE");
    }

    @Test
    void frozenAccountCanBeUnfrozenButNotFrozenTwice() {
        post("/freeze", "{\"reason\": \"FRAUD_SUSPECTED\"}");

        assertProblem(post("/freeze", "{\"reason\": \"COURT_ORDER\"}"), HttpStatus.CONFLICT, "INVALID_ACCOUNT_STATE");
        MvcTestResult unfrozen = post("/unfreeze", "");
        assertThat(unfrozen).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(unfrozen).bodyJson().extractingPath("$.statusReason").isNull();
    }

    @Test
    void anAccountHoldingMoneyCannotBeClosed() {
        assertProblem(post("/close", ""), HttpStatus.CONFLICT, "INVALID_ACCOUNT_STATE");
        assertThat(data.count("accounts WHERE status = 'CLOSED'")).isZero();
    }

    @Test
    void closedIsTerminal() {
        MvcTestResult closed = mvc.post()
                .uri("/admin/accounts/" + EMPTY_ACCOUNT + "/close")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange();
        assertThat(closed).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");

        MvcTestResult reopen = mvc.post()
                .uri("/admin/accounts/" + EMPTY_ACCOUNT + "/unfreeze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange();
        assertProblem(reopen, HttpStatus.CONFLICT, "INVALID_ACCOUNT_STATE");
    }

    @Test
    void limitChangesAreStoredAndAuditedWithPreviousValues() {
        MvcTestResult result = put("/limits", "{\"perTransactionLimit\": \"5000\", \"dailyLimit\": \"20000.00\"}");

        assertThat(result).bodyJson().extractingPath("$.perTransactionLimit").isEqualTo("5000.00");
        assertThat(result).bodyJson().extractingPath("$.dailyLimit").isEqualTo("20000.00");
        assertThat(jdbc.sql("""
                        SELECT metadata ->> 'previousDailyLimit' || '->' || (metadata ->> 'dailyLimit')
                        FROM audit_events WHERE action = 'TRANSFER_LIMITS_CHANGED'
                        """).query(String.class).single()).isEqualTo("->20000.00");
    }

    @Test
    void limitsMustBePositive() {
        assertProblem(put("/limits", "{\"perTransactionLimit\": \"-1\"}"), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }

    @Test
    void customersCannotAdministerAccountsEvenWithTheScope() {
        MvcTestResult result = mvc.post()
                .uri("/admin/accounts/" + ALICE_ACCOUNT + "/unfreeze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token("alice-sub", "bank.accounts.admin"))
                .exchange();

        assertProblem(result, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void administratorsNeedTheAdminScope() {
        MvcTestResult result = mvc.post()
                .uri("/admin/accounts/" + ALICE_ACCOUNT + "/freeze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token("admin-sub", "bank.accounts.read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"COURT_ORDER\"}")
                .exchange();

        assertProblem(result, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void administratorsCannotMoveCustomerMoney() {
        MvcTestResult result = mvc.post()
                .uri("/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token("admin-sub", "bank.transfer"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "r1", "fromAccountId": 100, "toAccountId": 101,
                         "amount": "1.00", "currency": "HKD"}
                        """)
                .exchange();

        assertProblem(result, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void unknownAccountIsNotFound() {
        MvcTestResult result = mvc.post()
                .uri("/admin/accounts/999/unfreeze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange();

        assertProblem(result, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
    }

    private MvcTestResult post(String action, String body) {
        return mvc.post()
                .uri("/admin/accounts/" + ALICE_ACCOUNT + action)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.isEmpty() ? "{}" : body)
                .exchange();
    }

    private MvcTestResult put(String action, String body) {
        return mvc.put()
                .uri("/admin/accounts/" + ALICE_ACCOUNT + action)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
