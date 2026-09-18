package io.github.zhlzxa.corebanking.outbox;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes events to Kafka. The event id and type travel as headers, so consumers can deduplicate
 * and route without parsing the body. The call waits for the broker's acknowledgement; only then is
 * the event marked as published.
 */
@Component
@EnableConfigurationProperties(OutboxProperties.class)
class KafkaEventPublisher implements EventPublisher {

    static final String EVENT_ID_HEADER = "eventId";
    static final String EVENT_TYPE_HEADER = "eventType";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, OutboxProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(UUID eventId, String eventType, String key, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(properties.topic(), key, payload);
        record.headers().add(EVENT_ID_HEADER, eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EVENT_TYPE_HEADER, eventType.getBytes(StandardCharsets.UTF_8));
        try {
            kafkaTemplate.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing event " + eventId, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Broker did not acknowledge event " + eventId, e);
        }
    }
}
