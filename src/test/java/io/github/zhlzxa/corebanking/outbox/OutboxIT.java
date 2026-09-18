package io.github.zhlzxa.corebanking.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import io.github.zhlzxa.corebanking.ledger.LedgerRepository;
import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestJwts;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The outbox is written with the business change and published at least once, against a mocked broker. */
class OutboxIT extends AbstractIntegrationIT {

    @MockitoBean
    private EventPublisher eventPublisher;

    @MockitoSpyBean
    private LedgerRepository ledgerRepository;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seed() {
        long alice = data.createCustomer("alice-sub");
        long bob = data.createCustomer("bob-sub");
        data.createAccount(100, alice, "HKD", "1000.00");
        data.createAccount(200, bob, "HKD", "0.00");
    }

    @Test
    void aCompletedTransferWritesExactlyOneEventWithoutSensitiveData() {
        assertThat(transfer("req-1", "100.00")).hasStatus(HttpStatus.CREATED);

        Map<String, Object> event = jdbc.sql("""
                        SELECT event_type, aggregate_id, payload ->> 'amount' AS amount, payload::text AS payload
                        FROM outbox_events
                        """).query().singleRow();
        Long transactionId =
                jdbc.sql("SELECT id FROM transactions").query(Long.class).single();
        assertThat(event)
                .containsEntry("event_type", "TransactionCompleted")
                .containsEntry("aggregate_id", transactionId.toString())
                .containsEntry("amount", "100.00");
        assertThat((String) event.get("payload")).doesNotContain("alice", "Bearer", "subject");
    }

    @Test
    void aRolledBackTransferWritesNoEvent() {
        doThrow(new DataAccessResourceFailureException("Simulated storage failure"))
                .when(ledgerRepository)
                .append(any());

        assertThat(transfer("req-1", "100.00")).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(data.count("outbox_events")).isZero();
    }

    @Test
    void anIdempotentRetryWritesNoSecondEvent() {
        transfer("req-1", "100.00");
        transfer("req-1", "100.00");

        assertThat(data.count("outbox_events")).isEqualTo(1);
    }

    @Test
    void publishedEventsAreMarkedAndNotPublishedAgain() {
        transfer("req-1", "100.00");
        doNothing().when(eventPublisher).publish(any(), anyString(), anyString(), anyString());

        assertThat(publisher.publishDueEvents()).isEqualTo(1);
        assertThat(publisher.publishDueEvents()).isZero();
        assertThat(outboxRepository.countUnpublished()).isZero();
    }

    @Test
    void aFailedPublicationIsRetriedLaterWithBackOff() {
        transfer("req-1", "100.00");
        doThrow(new IllegalStateException("broker unavailable"))
                .when(eventPublisher)
                .publish(any(), anyString(), anyString(), anyString());

        assertThat(publisher.publishDueEvents()).isZero();

        Map<String, Object> row = jdbc.sql("""
                        SELECT attempt_count, published_at IS NULL AS unpublished,
                               next_attempt_at > now() AS deferred, last_error
                        FROM outbox_events
                        """).query().singleRow();
        assertThat(row)
                .containsEntry("attempt_count", 1)
                .containsEntry("unpublished", true)
                .containsEntry("deferred", true);
        assertThat((String) row.get("last_error")).contains("broker unavailable");
        assertThat(outboxRepository.oldestUnpublishedAt()).isPresent();

        doNothing().when(eventPublisher).publish(any(), anyString(), anyString(), anyString());
        jdbc.sql("UPDATE outbox_events SET next_attempt_at = :now")
                .param("now", OffsetDateTime.now())
                .update();
        assertThat(publisher.publishDueEvents()).isEqualTo(1);
    }

    @Test
    void concurrentPublishersClaimDisjointEvents() throws Exception {
        for (int i = 0; i < 20; i++) {
            transfer("req-" + i, "1.00");
        }
        Instant now = Instant.now().plusSeconds(1);

        CompletableFuture<List<OutboxRepository.PendingEvent>> first =
                CompletableFuture.supplyAsync(() -> outboxRepository.claimDue(now, now.plusSeconds(30), 15));
        CompletableFuture<List<OutboxRepository.PendingEvent>> second =
                CompletableFuture.supplyAsync(() -> outboxRepository.claimDue(now, now.plusSeconds(30), 15));

        Set<Long> ids = new HashSet<>();
        first.get().forEach(event -> ids.add(event.id()));
        second.get().forEach(event -> ids.add(event.id()));
        assertThat(ids).hasSize(first.get().size() + second.get().size()).hasSize(20);
    }

    @Test
    void cleanupDeletesOnlyPublishedEventsPastRetention() {
        transfer("req-1", "1.00");
        transfer("req-2", "1.00");
        transfer("req-3", "1.00");
        jdbc.sql("UPDATE outbox_events SET published_at = now() - interval '8 days' WHERE aggregate_id IN "
                        + "(SELECT id::text FROM transactions WHERE request_id = 'req-1')")
                .update();
        jdbc.sql("UPDATE outbox_events SET published_at = now() - interval '1 day' WHERE aggregate_id IN "
                        + "(SELECT id::text FROM transactions WHERE request_id = 'req-2')")
                .update();

        assertThat(publisher.deleteExpiredEvents()).isEqualTo(1);
        assertThat(data.count("outbox_events")).isEqualTo(2);
    }

    private MvcTestResult transfer(String requestId, String amount) {
        return mvc.post()
                .uri("/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token("alice-sub", "bank.transfer"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "fromAccountId": 100, "toAccountId": 200,
                         "amount": "%s", "currency": "HKD"}
                        """.formatted(requestId, amount))
                .exchange();
    }
}
