package io.github.zhlzxa.corebanking.notification;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Receives transaction events from the broker and hands them to {@link NotificationService}. */
@Component
class TransactionEventListener {

    static final String LISTENER_ID = "account-notifications";

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final NotificationService notificationService;

    TransactionEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
            id = LISTENER_ID,
            groupId = NotificationService.CONSUMER,
            topics = "${corebanking.outbox.topic}",
            autoStartup = "${corebanking.notifications.enabled:true}")
    void onTransactionEvent(ConsumerRecord<String, String> record) {
        Header eventType = record.headers().lastHeader("eventType");
        Header eventId = record.headers().lastHeader("eventId");
        if (eventType == null || eventId == null) {
            log.warn("Ignoring event without id or type: partition={}, offset={}", record.partition(), record.offset());
            return;
        }
        if (!"TransactionCompleted".equals(new String(eventType.value(), StandardCharsets.UTF_8))) {
            return;
        }
        UUID id = UUID.fromString(new String(eventId.value(), StandardCharsets.UTF_8));
        if (!notificationService.handleTransactionCompleted(id, record.value())) {
            log.info("Duplicate delivery ignored: eventId={}", id);
        }
    }
}
