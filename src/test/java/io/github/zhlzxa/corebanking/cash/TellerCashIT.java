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

class TellerCashIT extends AbstractIntegrationIT {

    private static final long ALICE = 100;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private JdbcClient jdbc;

    private long alice;
    private long teller;
    private long cashHkd;
    private final String tellerToken = TestJwts.builder("teller-sub")
            .scopes("bank.cash")
            .claim("branch_code", "BR-001")
            .build();

    @BeforeEach
    void seed() {
        alice = data.createCustomer("alice-sub");
        teller = data.createUser("teller-sub", "TELLER", "ACTIVE");
        data.createAccount(ALICE, alice, "HKD", "1000.00");
        cashHkd = data.createInternalAccount("CASH-HKD", "HKD");
    }

    @Test
    void depositCreditsTheCustomerAgainstTheCashAccount() {
        MvcTestResult result = cash("/teller/deposits", tellerToken, "dep-1", "500.00", "HKD");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("DEPOSIT");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("1500.00");
        assertThat(data.balanceOf(cashHkd)).isEqualByComparingTo("-500.00");
        assertThat(data.unbalancedTransactions()).isZero();
    }

    @Test
    void cashOperationsAreAuditedAsTheTellerOnBehalfOfTheCustomer() {
        cash("/teller/deposits", tellerToken, "dep-1", "500.00", "HKD");

        Map<String, Object> event = jdbc.sql("""
                        SELECT actor_user_id, actor_branch_code, on_behalf_of_user_id, channel
                        FROM audit_events WHERE action = 'CASH_DEPOSIT_COMPLETED'
                        """).query().singleRow();
        assertThat(event)
                .containsEntry("actor_user_id", teller)
                .containsEntry("actor_branch_code", "BR-001")
                .containsEntry("on_behalf_of_user_id", alice)
                .containsEntry("channel", "BRANCH");
        assertThat(jdbc.sql("SELECT initiated_by_user_id FROM transactions")
                        .query(Long.class)
                        .single())
                .isEqualTo(teller);
    }

    @Test
    void withdrawalDebitsTheCustomerAndCannotOverdraw() {
        assertThat(cash("/teller/withdrawals", tellerToken, "wd-1", "200.00", "HKD"))
                .hasStatus(HttpStatus.CREATED);
        MvcTestResult tooMuch = cash("/teller/withdrawals", tellerToken, "wd-2", "800.01", "HKD");

        assertProblem(tooMuch, HttpStatus.CONFLICT, "INSUFFICIENT_BALANCE");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("800.00");
        assertThat(data.balanceOf(cashHkd)).isEqualByComparingTo("200.00");
        assertThat(data.count("audit_events WHERE action = 'CASH_WITHDRAWAL_REJECTED'"))
                .isEqualTo(1);
    }

    @Test
    void frozenAccountAcceptsDepositsButNoWithdrawals() {
        data.setStatus(ALICE, "FROZEN", "COURT_ORDER");

        assertThat(cash("/teller/deposits", tellerToken, "dep-1", "10.00", "HKD"))
                .hasStatus(HttpStatus.CREATED);
        assertProblem(
                cash("/teller/withdrawals", tellerToken, "wd-1", "10.00", "HKD"),
                HttpStatus.CONFLICT,
                "SOURCE_ACCOUNT_NOT_ACTIVE");
    }

    @Test
    void aRepeatedRequestIsPostedOnce() {
        cash("/teller/deposits", tellerToken, "dep-1", "500.00", "HKD");
        MvcTestResult retry = cash("/teller/deposits", tellerToken, "dep-1", "500.00", "HKD");

        assertThat(retry).hasStatus(HttpStatus.CREATED);
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("1500.00");
        assertThat(data.count("transactions")).isEqualTo(1);
    }

    @Test
    void currencyWithoutCashAccountIsNotSupported() {
        assertProblem(
                cash("/teller/deposits", tellerToken, "dep-1", "500", "JPY"),
                HttpStatus.BAD_REQUEST,
                "CURRENCY_NOT_SUPPORTED");
    }

    @Test
    void tellerWithoutBranchCannotHandleCash() {
        String noBranch = TestJwts.token("teller-sub", "bank.cash");

        assertProblem(
                cash("/teller/deposits", noBranch, "dep-1", "10.00", "HKD"), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertThat(data.balanceOf(ALICE)).isEqualByComparingTo("1000.00");
    }

    @Test
    void customersCannotUseTellerOperations() {
        String customer = TestJwts.token("alice-sub", "bank.cash");

        assertProblem(
                cash("/teller/deposits", customer, "dep-1", "10.00", "HKD"), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void depositAppearsInTheCustomersHistory() {
        cash("/teller/deposits", tellerToken, "dep-1", "500.00", "HKD");

        MvcTestResult history = mvc.get()
                .uri("/accounts/" + ALICE + "/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token("alice-sub", "bank.accounts.read"))
                .exchange();

        assertThat(history).bodyJson().extractingPath("$.items[0].type").isEqualTo("DEPOSIT");
        assertThat(history).bodyJson().extractingPath("$.items[0].direction").isEqualTo("CREDIT");
    }

    private MvcTestResult cash(String uri, String token, String requestId, String amount, String currency) {
        return mvc.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "accountId": %d, "amount": "%s", "currency": "%s"}
                        """.formatted(requestId, ALICE, amount, currency))
                .exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
