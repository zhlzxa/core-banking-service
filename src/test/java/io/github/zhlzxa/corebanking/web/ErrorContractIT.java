package io.github.zhlzxa.corebanking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.github.zhlzxa.corebanking.ledger.LedgerRepository;
import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestJwts;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Verifies the error contract that holds for every failure, whichever layer raises it: a problem
 * body with a stable code, the request's correlation id, and no internal details.
 */
class ErrorContractIT extends AbstractIntegrationIT {

    @MockitoSpyBean
    private LedgerRepository ledgerRepository;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @BeforeEach
    void seed() {
        long alice = data.createCustomer("alice-sub");
        long bob = data.createCustomer("bob-sub");
        data.createAccount(100, alice, "HKD", "50.00");
        data.createAccount(200, bob, "HKD", "0.00");
    }

    @Test
    void businessErrorCarriesCodeAndCallerCorrelationId() {
        MvcTestResult result = transfer(TestJwts.token("alice-sub", "bank.transfer"), "999.00", "test-123");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).hasHeader(CorrelationId.HEADER, "test-123");
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(result).bodyJson().extractingPath("$.correlationId").isEqualTo("test-123");
    }

    @Test
    void validationErrorCarriesCorrelationId() {
        MvcTestResult result = transfer(TestJwts.token("alice-sub", "bank.transfer"), "-1", "test-456");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.correlationId").isEqualTo("test-456");
    }

    @Test
    void authenticationFailureIsAProblemWithCorrelationIdAndChallenge() {
        MvcTestResult result = transfer("not-a-jwt", "1.00", "test-789");

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).headers().containsHeader(HttpHeaders.WWW_AUTHENTICATE);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
        assertThat(result).bodyJson().extractingPath("$.correlationId").isEqualTo("test-789");
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/transfers");
    }

    @Test
    void unknownPathIsAProblemToo() {
        MvcTestResult result = mvc.get()
                .uri("/no-such-endpoint")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token("alice-sub"))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.correlationId").isNotNull();
    }

    @Test
    void unexpectedFailureRevealsNoInternals() throws Exception {
        doThrow(new BadSqlGrammarException(
                        "append", "INSERT INTO ledger_entries (secret_column)", new SQLException("relation missing")))
                .when(ledgerRepository)
                .append(any());

        MvcTestResult result = transfer(TestJwts.token("alice-sub", "bank.transfer"), "1.00", "test-500");

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");
        assertThat(result).bodyJson().extractingPath("$.correlationId").isEqualTo("test-500");
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContainIgnoringCase("sql")
                .doesNotContain("ledger_entries", "secret_column", "Exception", "at io.github");
        assertThat(data.balanceOf(100)).isEqualByComparingTo("50.00");
    }

    private MvcTestResult transfer(String token, String amount, String correlationId) {
        return mvc.post()
                .uri("/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(CorrelationId.HEADER, correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "req-err", "fromAccountId": 100, "toAccountId": 200,
                         "amount": "%s", "currency": "HKD"}
                        """.formatted(amount))
                .exchange();
    }
}
