package io.github.zhlzxa.corebanking.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestJwts;
import io.github.zhlzxa.corebanking.web.CorrelationId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Verifies that requests stopped by the security layer leave an audit record. */
class SecurityAuditIT extends AbstractIntegrationIT {

    @MockitoSpyBean
    private AuditEventRepository auditEventRepository;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private JdbcClient jdbc;

    private long alice;

    @BeforeEach
    void seed() {
        alice = data.createCustomer("alice-sub");
        long bob = data.createCustomer("bob-sub");
        data.createAccount(100, alice, "HKD", "1000.00");
        data.createAccount(200, bob, "HKD", "0.00");
    }

    @Test
    void missingTokenIsAuditedAsAnonymousAuthenticationFailure() {
        MvcTestResult result = transfer(null, "corr-401");

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        Map<String, Object> event = singleEvent("AUTHENTICATION_FAILED");
        assertThat(event)
                .containsEntry("actor_user_id", null)
                .containsEntry("outcome", "REJECTED")
                .containsEntry("reason_code", "UNAUTHENTICATED")
                .containsEntry("resource_type", "HTTP_ENDPOINT")
                .containsEntry("resource_id", "/transfers")
                .containsEntry("correlation_id", "corr-401");
    }

    @Test
    void missingScopeIsAuditedWithTheAuthenticatedActor() {
        MvcTestResult result = transfer(TestJwts.token("alice-sub", "bank.accounts.read"), "corr-403");

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(result).bodyJson().extractingPath("$.correlationId").isEqualTo("corr-403");
        Map<String, Object> event = singleEvent("AUTHORIZATION_DENIED");
        assertThat(event)
                .containsEntry("actor_user_id", alice)
                .containsEntry("reason_code", "ACCESS_DENIED")
                .containsEntry("correlation_id", "corr-403");
        assertThat(data.count("transactions")).isZero();
    }

    @Test
    void auditFailureNeverTurnsARejectionIntoAServerError() {
        doThrow(new IllegalStateException("Simulated audit store failure"))
                .when(auditEventRepository)
                .append(any());

        assertThat(transfer(null, "corr-a")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(transfer(TestJwts.token("alice-sub"), "corr-b")).hasStatus(HttpStatus.FORBIDDEN);
    }

    private MvcTestResult transfer(String token, String correlationId) {
        var request = mvc.post()
                .uri("/transfers")
                .header(CorrelationId.HEADER, correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "req-sec", "fromAccountId": 100, "toAccountId": 200,
                         "amount": "1.00", "currency": "HKD"}
                        """);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.exchange();
    }

    private Map<String, Object> singleEvent(String action) {
        List<Map<String, Object>> rows =
                jdbc.sql("""
                        SELECT actor_user_id, outcome, reason_code, resource_type, resource_id, correlation_id
                        FROM audit_events WHERE action = :action
                        """).param("action", action).query().listOfRows();
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }
}
