package io.github.zhlzxa.corebanking.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestJwts;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.kafka.KafkaContainer;

/**
 * The outbox end to end against a real Kafka broker: publication, consumption and consumer-side
 * deduplication of redelivered events.
 */
class OutboxKafkaIT extends AbstractIntegrationIT {

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.1.0");

    static {
        KAFKA.start();
    }

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private KafkaListenerEndpointRegistry listeners;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seedAndStartConsumer() {
        long alice = data.createCustomer("alice-sub");
        long bob = data.createCustomer("bob-sub");
        data.createAccount(100, alice, "HKD", "1000.00");
        data.createAccount(200, bob, "HKD", "0.00");
        listeners.getListenerContainer("account-notifications").start();
    }

    @AfterEach
    void stopConsumer() {
        listeners.getListenerContainer("account-notifications").stop();
    }

    @Test
    void aCompletedTransferReachesTheConsumerThroughKafka() {
        mvc.post()
                .uri("/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token("alice-sub", "bank.transfer"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "req-kafka", "fromAccountId": 100, "toAccountId": 200,
                         "amount": "25.00", "currency": "HKD"}
                        """)
                .exchange();

        assertThat(outboxPublisher.publishDueEvents()).isEqualTo(1);
        long transactionId = jdbc.sql("SELECT id FROM transactions WHERE request_id = 'req-kafka'")
                .query(Long.class)
                .single();

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> assertThat(notificationsOf(transactionId)).isEqualTo("MONEY_IN:200,MONEY_OUT:100"));
    }

    @Test
    void aRedeliveredEventIsHandledOnlyOnce() {
        // An explicit id that no transfer in this class can have: identities restart with every
        // test, and an event left on the topic by an earlier test must not be counted here.
        long transactionId = 900;
        jdbc.sql("""
                        INSERT INTO transactions
                            (id, request_id, transaction_type, status, from_account_id, to_account_id, amount, currency)
                        VALUES (:id, 'seeded', 'TRANSFER', 'COMPLETED', 100, 200, 5, 'HKD')
                        """).param("id", transactionId).update();
        UUID eventId = UUID.randomUUID();
        String payload = """
                {"transactionId": %d, "fromAccountId": 100, "toAccountId": 200}
                """.formatted(transactionId);

        eventPublisher.publish(eventId, "TransactionCompleted", String.valueOf(transactionId), payload);
        eventPublisher.publish(eventId, "TransactionCompleted", String.valueOf(transactionId), payload);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(data.count("processed_events WHERE event_id = '" + eventId + "'"))
                        .isEqualTo(1));
        await().during(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> assertThat(notificationsOf(transactionId)).isEqualTo("MONEY_IN:200,MONEY_OUT:100"));
    }

    /**
     * The notifications recorded for one transaction, for example {@code MONEY_IN:200,MONEY_OUT:100};
     * empty while the consumer has not handled the event yet.
     */
    private String notificationsOf(long transactionId) {
        return jdbc.sql("""
                        SELECT coalesce(string_agg(kind || ':' || account_id, ',' ORDER BY kind), '')
                        FROM account_notifications
                        WHERE transaction_id = :transactionId
                        """)
                .param("transactionId", transactionId)
                .query(String.class)
                .single();
    }
}
