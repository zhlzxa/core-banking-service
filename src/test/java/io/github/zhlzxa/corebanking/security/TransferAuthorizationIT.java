package io.github.zhlzxa.corebanking.security;

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

/**
 * Verifies the three authorization layers for transfers: OAuth scope, bank role and account
 * ownership.
 */
class TransferAuthorizationIT extends AbstractIntegrationIT {

    private static final long ALICE_ACCOUNT = 100;
    private static final long BOB_ACCOUNT = 200;
    private static final long INTERNAL_ACCOUNT = 900;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @BeforeEach
    void seed() {
        long alice = data.createCustomer("alice-sub");
        long bob = data.createCustomer("bob-sub");
        data.createUser("teller-sub", "TELLER", "ACTIVE");
        data.createUser("admin-sub", "ADMIN", "ACTIVE");
        data.createAccount(ALICE_ACCOUNT, alice, "HKD", "1000.00");
        data.createAccount(BOB_ACCOUNT, bob, "HKD", "1000.00");
        data.createAccount(INTERNAL_ACCOUNT, null, "HKD", "0.00");
    }

    @Test
    void customerCanTransferFromOwnAccountToAnotherCustomer() {
        MvcTestResult result = transfer(TestJwts.token("alice-sub", "bank.transfer"), ALICE_ACCOUNT, BOB_ACCOUNT);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(data.balanceOf(BOB_ACCOUNT)).isEqualByComparingTo("1010.00");
    }

    @Test
    void tokenWithoutTransferScopeIsForbidden() {
        MvcTestResult result = transfer(TestJwts.token("alice-sub", "bank.accounts.read"), ALICE_ACCOUNT, BOB_ACCOUNT);

        assertProblem(result, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertThat(data.count("transactions")).isZero();
    }

    @Test
    void staffRolesCannotUseTheCustomerTransferEndpoint() {
        assertProblem(
                transfer(TestJwts.token("teller-sub", "bank.transfer"), ALICE_ACCOUNT, BOB_ACCOUNT),
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED");
        assertProblem(
                transfer(TestJwts.token("admin-sub", "bank.transfer"), ALICE_ACCOUNT, BOB_ACCOUNT),
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED");
        assertThat(data.balanceOf(ALICE_ACCOUNT)).isEqualByComparingTo("1000.00");
    }

    @Test
    void anotherCustomersAccountIsIndistinguishableFromAMissingOne() {
        MvcTestResult stealing = transfer(TestJwts.token("bob-sub", "bank.transfer"), ALICE_ACCOUNT, BOB_ACCOUNT);
        MvcTestResult missing = transfer(TestJwts.token("bob-sub", "bank.transfer"), 12345, BOB_ACCOUNT);

        assertProblem(stealing, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        assertProblem(missing, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        assertThat(data.balanceOf(ALICE_ACCOUNT)).isEqualByComparingTo("1000.00");
        assertThat(data.count("transactions")).isZero();
    }

    @Test
    void bankInternalAccountsCannotBeUsedAsDestination() {
        MvcTestResult result = transfer(TestJwts.token("alice-sub", "bank.transfer"), ALICE_ACCOUNT, INTERNAL_ACCOUNT);

        assertProblem(result, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        assertThat(data.balanceOf(INTERNAL_ACCOUNT)).isEqualByComparingTo("0.00");
    }

    @Test
    void requestIdOfAnotherCustomerIsNotTreatedAsARetry() {
        transfer(TestJwts.token("alice-sub", "bank.transfer"), ALICE_ACCOUNT, BOB_ACCOUNT);

        MvcTestResult result = transfer(TestJwts.token("bob-sub", "bank.transfer"), BOB_ACCOUNT, ALICE_ACCOUNT);

        assertProblem(result, HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
        assertThat(data.count("transactions")).isEqualTo(1);
    }

    private MvcTestResult transfer(String token, long from, long to) {
        return mvc.post()
                .uri("/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "req-authz", "fromAccountId": %d, "toAccountId": %d,
                         "amount": "10.00", "currency": "HKD"}
                        """.formatted(from, to))
                .exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
