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

/** Verifies that only valid tokens of known, active users reach the API. */
class AuthenticationIT extends AbstractIntegrationIT {

    private static final String BODY = """
            {"requestId": "req-auth", "fromAccountId": 100, "toAccountId": 200,
             "amount": "1.00", "currency": "HKD"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @BeforeEach
    void seed() {
        long alice = data.createCustomer("alice-sub");
        long bob = data.createCustomer("bob-sub");
        data.createUser("locked-sub", "CUSTOMER", "LOCKED");
        data.createUser("disabled-sub", "CUSTOMER", "DISABLED");
        data.createAccount(100, alice, "HKD", "100.00");
        data.createAccount(200, bob, "HKD", "0.00");
    }

    @Test
    void validTokenOfActiveUserIsAccepted() {
        assertThat(post(TestJwts.token("alice-sub", "bank.transfer"))).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void missingTokenIsUnauthorized() {
        MvcTestResult result = mvc.post()
                .uri("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(data.count("transactions")).isZero();
    }

    @Test
    void tokenSignedByAnUntrustedKeyIsUnauthorized() {
        assertUnauthorized(TestJwts.builder("alice-sub")
                .scopes("bank.transfer")
                .signedByUntrustedKey()
                .build());
    }

    @Test
    void expiredTokenIsUnauthorized() {
        assertUnauthorized(TestJwts.builder("alice-sub")
                .scopes("bank.transfer")
                .expiredMinutesAgo(10)
                .build());
    }

    @Test
    void tokenForAnotherAudienceIsUnauthorized() {
        assertUnauthorized(TestJwts.builder("alice-sub")
                .scopes("bank.transfer")
                .audience("another-api")
                .build());
    }

    @Test
    void tokenFromAnotherIssuerIsUnauthorized() {
        assertUnauthorized(TestJwts.builder("alice-sub")
                .scopes("bank.transfer")
                .issuer("https://evil.test")
                .build());
    }

    @Test
    void identityUnknownToTheBankIsUnauthorized() {
        assertUnauthorized(TestJwts.token("stranger-sub", "bank.transfer"));
    }

    @Test
    void lockedAndDisabledUsersAreUnauthorized() {
        assertUnauthorized(TestJwts.token("locked-sub", "bank.transfer"));
        assertUnauthorized(TestJwts.token("disabled-sub", "bank.transfer"));
    }

    @Test
    void healthEndpointIsPublic() {
        assertThat(mvc.get().uri("/actuator/health").exchange()).hasStatus(HttpStatus.OK);
    }

    private void assertUnauthorized(String token) {
        assertThat(post(token)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(data.count("transactions")).isZero();
    }

    private MvcTestResult post(String token) {
        return mvc.post()
                .uri("/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .exchange();
    }
}
