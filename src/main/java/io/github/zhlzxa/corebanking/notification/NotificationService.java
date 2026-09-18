package io.github.zhlzxa.corebanking.notification;

import io.github.zhlzxa.corebanking.account.Account;
import io.github.zhlzxa.corebanking.account.AccountRepository;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Example consumer of transaction events: records a notification for each customer account that
 * money left or arrived in.
 *
 * <p>Events are delivered at least once. The event id is recorded in {@code processed_events} in the
 * same transaction as the notifications, so a redelivered event is recognised and ignored, and a
 * failure leaves neither behind.
 */
@Service
public class NotificationService {

    static final String CONSUMER = "account-notifications";

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final AccountRepository accountRepository;

    public NotificationService(JdbcClient jdbc, JsonMapper jsonMapper, AccountRepository accountRepository) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.accountRepository = accountRepository;
    }

    /**
     * Handles one {@code TransactionCompleted} event.
     *
     * @return {@code false} if the event had already been handled
     */
    @Transactional
    public boolean handleTransactionCompleted(UUID eventId, String payload) {
        boolean firstDelivery = jdbc.sql("""
                        INSERT INTO processed_events (consumer, event_id) VALUES (:consumer, :eventId)
                        ON CONFLICT DO NOTHING
                        RETURNING event_id
                        """)
                .param("consumer", CONSUMER)
                .param("eventId", eventId)
                .query(UUID.class)
                .optional()
                .isPresent();
        if (!firstDelivery) {
            return false;
        }
        JsonNode event = jsonMapper.readTree(payload);
        long transactionId = event.get("transactionId").asLong();
        notifyIfCustomerAccount(event.get("fromAccountId").asLong(), transactionId, "MONEY_OUT");
        notifyIfCustomerAccount(event.get("toAccountId").asLong(), transactionId, "MONEY_IN");
        return true;
    }

    private void notifyIfCustomerAccount(long accountId, long transactionId, String kind) {
        boolean customerAccount = accountRepository
                .findById(accountId)
                .map(Account::isCustomerAccount)
                .orElse(false);
        if (customerAccount) {
            jdbc.sql("""
                            INSERT INTO account_notifications (account_id, transaction_id, kind)
                            VALUES (:accountId, :transactionId, :kind)
                            """)
                    .param("accountId", accountId)
                    .param("transactionId", transactionId)
                    .param("kind", kind)
                    .update();
        }
    }
}
