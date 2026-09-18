package io.github.zhlzxa.corebanking.outbox;

import io.github.zhlzxa.corebanking.common.money.MoneyFormatter;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds the integration events this service publishes. */
@Component
public class IntegrationEvents {

    public static final String TRANSACTION = "TRANSACTION";
    public static final String TRANSACTION_COMPLETED = "TransactionCompleted";

    private final Clock clock;

    public IntegrationEvents(Clock clock) {
        this.clock = clock;
    }

    /**
     * Money has moved for good. The payload is deliberately "fat" so that consumers do not have to
     * call back, but it contains only internal identifiers and amounts.
     */
    public OutboxEvent transactionCompleted(BankTransaction transaction) {
        UUID eventId = UUID.randomUUID();
        return new OutboxEvent(
                eventId,
                TRANSACTION,
                Long.toString(transaction.id()),
                TRANSACTION_COMPLETED,
                Map.of(
                        "eventId", eventId.toString(),
                        "transactionId", transaction.id(),
                        "transactionType", transaction.type().name(),
                        "fromAccountId", transaction.fromAccountId(),
                        "toAccountId", transaction.toAccountId(),
                        "amount", MoneyFormatter.format(transaction.amount(), transaction.currency()),
                        "currency", transaction.currency(),
                        "occurredAt", clock.instant().toString()),
                clock.instant());
    }
}
