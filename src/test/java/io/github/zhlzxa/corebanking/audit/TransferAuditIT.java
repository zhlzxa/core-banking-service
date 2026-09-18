package io.github.zhlzxa.corebanking.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
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

/**
 * Verifies the audit trail of transfers end to end: what is recorded for each outcome, that success
 * auditing is atomic with the money movement, and that nothing sensitive is stored.
 */
class TransferAuditIT extends AbstractIntegrationIT {

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
        data.createAccount(200, bob, "HKD", "500.00");
    }

    @Test
    void completedTransferIsAuditedWithActorTransactionAndCorrelationId() {
        MvcTestResult result = transfer("req-ok", "100.00", "corr-ok");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        Map<String, Object> event = singleEvent("TRANSFER_COMPLETED");
        assertThat(event)
                .containsEntry("outcome", "SUCCESS")
                .containsEntry("actor_user_id", alice)
                .containsEntry("actor_subject", "alice-sub")
                .containsEntry("actor_role", "CUSTOMER")
                .containsEntry("request_id", "req-ok")
                .containsEntry("correlation_id", "corr-ok")
                .containsEntry("channel", "API");
        Long transactionId = jdbc.sql("SELECT id FROM transactions WHERE request_id = 'req-ok'")
                .query(Long.class)
                .single();
        assertThat(event.get("transaction_id")).isEqualTo(transactionId);
    }

    @Test
    void idempotentRetryIsNotAuditedTwice() {
        transfer("req-retry", "100.00", "corr-1");
        transfer("req-retry", "100.00", "corr-2");

        assertThat(events("TRANSFER_COMPLETED")).hasSize(1);
        assertThat(data.balanceOf(100)).isEqualByComparingTo("900.00");
    }

    @Test
    void rejectedTransferIsAuditedAlthoughNothingElseIsPersisted() {
        MvcTestResult result = transfer("req-big", "5000.00", "corr-rejected");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        Map<String, Object> event = singleEvent("TRANSFER_REJECTED");
        assertThat(event)
                .containsEntry("outcome", "REJECTED")
                .containsEntry("reason_code", "INSUFFICIENT_BALANCE")
                .containsEntry("transaction_id", null)
                .containsEntry("request_id", "req-big")
                .containsEntry("correlation_id", "corr-rejected")
                .containsEntry("amount", "5000.00");
        assertThat(data.count("transactions")).isZero();
    }

    @Test
    void successAuditFailureRollsBackTheMoneyMovement() {
        doThrow(new IllegalStateException("Simulated audit store failure"))
                .when(auditEventRepository)
                .append(argThat(event -> event.action() == AuditAction.TRANSFER_COMPLETED));

        MvcTestResult result = transfer("req-no-audit", "100.00", "corr-x");

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(data.balanceOf(100)).isEqualByComparingTo("1000.00");
        assertThat(data.balanceOf(200)).isEqualByComparingTo("500.00");
        assertThat(data.count("transactions")).isZero();
        assertThat(data.count("ledger_entries")).isZero();
        assertThat(singleEvent("TRANSFER_FAILED")).containsEntry("reason_code", "INTERNAL_ERROR");
    }

    @Test
    void failureToAuditARejectionDoesNotChangeTheResponse() {
        doThrow(new IllegalStateException("Simulated audit store failure"))
                .when(auditEventRepository)
                .append(argThat(event -> event.action() == AuditAction.TRANSFER_REJECTED));

        MvcTestResult result = transfer("req-big", "5000.00", "corr-y");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    void auditTrailNeverContainsTheAccessToken() {
        String token = TestJwts.token("alice-sub", "bank.transfer");
        post(token, "req-secret", "100.00", "corr-z");
        post(token, "req-secret-2", "9999.00", "corr-z");

        String everything = jdbc.sql("SELECT string_agg(audit_events::text, '|') FROM audit_events")
                .query(String.class)
                .single();
        assertThat(everything).doesNotContain(token).doesNotContainIgnoringCase("bearer");
    }

    private MvcTestResult transfer(String requestId, String amount, String correlationId) {
        return post(TestJwts.token("alice-sub", "bank.transfer"), requestId, amount, correlationId);
    }

    private MvcTestResult post(String token, String requestId, String amount, String correlationId) {
        return mvc.post()
                .uri("/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(CorrelationId.HEADER, correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "fromAccountId": 100, "toAccountId": 200,
                         "amount": "%s", "currency": "HKD"}
                        """.formatted(requestId, amount))
                .exchange();
    }

    private List<Map<String, Object>> events(String action) {
        return jdbc.sql("""
                        SELECT action, outcome, reason_code, actor_user_id, actor_subject, actor_role,
                               transaction_id, request_id, correlation_id, channel,
                               metadata ->> 'amount' AS amount
                        FROM audit_events WHERE action = :action
                        """).param("action", action).query().listOfRows();
    }

    private Map<String, Object> singleEvent(String action) {
        List<Map<String, Object>> rows = events(action);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }
}
