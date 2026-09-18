package io.github.zhlzxa.corebanking.cash;

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

/** Cash withdrawals by self-service terminals, which authenticate as machines rather than users. */
class AtmWithdrawalIT extends AbstractIntegrationIT {

    private static final long ALICE = 100;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private JdbcClient jdbc;

    private long alice;
    private final String atmToken = TestJwts.token("atm-client-001", "bank.atm.withdraw");

    @BeforeEach
    void seed() {
        alice = data.createCustomer("alice-sub");
        data.createAccount(ALICE, alice, "HKD", "30000.00");
        data.createInternalAccount("CASH-HKD", "HKD");
        data.createTerminal("ATM-HK-0001", "atm-client-001", "BR-001", "ACTIVE");
        data.createTerminal("ATM-HK-0002", "atm-client-002", "BR-001", "DISABLED");
    }

    @Test
    void terminalPaysOutCashAndIsAuditedAsTheMachine() {
        MvcTestResult result = withdraw(atmToken, "atm-1", "500.00");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("29500.00");
        Map<String, Object> event = jdbc.sql("""
                        SELECT actor_user_id, actor_terminal_id, actor_branch_code, on_behalf_of_user_id, channel
                        FROM audit_events WHERE action = 'CASH_WITHDRAWAL_COMPLETED'
                        """).query().singleRow();
        assertThat(event)
                .containsEntry("actor_user_id", null)
                .containsEntry("actor_terminal_id", "ATM-HK-0001")
                .containsEntry("actor_branch_code", "BR-001")
                .containsEntry("on_behalf_of_user_id", alice)
                .containsEntry("channel", "ATM");
        assertThat(data.count("transactions WHERE initiated_by_user_id IS NULL"))
                .isEqualTo(1);
    }

    @Test
    void withdrawalsAboveTheTerminalLimitAreRejected() {
        MvcTestResult result = withdraw(atmToken, "atm-1", "20000.01");

        assertProblem(result, HttpStatus.CONFLICT, "TRANSFER_LIMIT_EXCEEDED");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("30000.00");
    }

    @Test
    void aDisabledTerminalCannotAuthenticate() {
        MvcTestResult result = withdraw(TestJwts.token("atm-client-002", "bank.atm.withdraw"), "atm-1", "100.00");

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void terminalsCannotUsePeopleEndpoints() {
        String everything =
                TestJwts.token("atm-client-001", "bank.atm.withdraw", "bank.transfer", "bank.accounts.read");

        MvcTestResult transfer = mvc.post()
                .uri("/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + everything)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();
        MvcTestResult accounts = mvc.get()
                .uri("/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + everything)
                .exchange();

        assertProblem(transfer, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertProblem(accounts, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void peopleCannotUseTerminalEndpoints() {
        MvcTestResult result = withdraw(TestJwts.token("alice-sub", "bank.atm.withdraw"), "atm-1", "100.00");

        assertProblem(result, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("30000.00");
    }

    @Test
    void terminalsNeedTheWithdrawalScope() {
        MvcTestResult result = withdraw(TestJwts.token("atm-client-001", "bank.atm.balance"), "atm-1", "100.00");

        assertProblem(result, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    private MvcTestResult withdraw(String token, String requestId, String amount) {
        return mvc.post()
                .uri("/atm/withdrawals")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "accountId": %d, "amount": "%s", "currency": "HKD"}
                        """.formatted(requestId, ALICE, amount))
                .exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
