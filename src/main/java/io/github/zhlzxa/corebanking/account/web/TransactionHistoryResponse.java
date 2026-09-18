package io.github.zhlzxa.corebanking.account.web;

import io.github.zhlzxa.corebanking.account.TransactionHistoryPage;
import io.github.zhlzxa.corebanking.common.money.MoneyFormatter;
import io.github.zhlzxa.corebanking.ledger.EntryDirection;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A page of transaction history from the perspective of one account.
 *
 * <p>There is deliberately no total count or page number: counting a long history on every request
 * is expensive, and positions shift as new transactions arrive. Clients follow {@code nextCursor}.
 */
public record TransactionHistoryResponse(
        List<Item> items, @Nullable String nextCursor, boolean hasMore) {

    /**
     * @param direction {@code DEBIT} if money left the account, {@code CREDIT} if it arrived
     * @param counterpartyAccountId the other account of the transaction
     */
    public record Item(
            long transactionId,
            String type,
            EntryDirection direction,
            long counterpartyAccountId,
            String amount,
            String currency,
            String status,
            Instant occurredAt) {}

    static TransactionHistoryResponse from(TransactionHistoryPage page) {
        List<Item> items = page.transactions().stream()
                .map(transaction -> item(page.accountId(), transaction))
                .toList();
        return new TransactionHistoryResponse(items, page.nextCursor(), page.hasMore());
    }

    private static Item item(long accountId, BankTransaction transaction) {
        boolean outgoing = transaction.fromAccountId() == accountId;
        return new Item(
                transaction.id(),
                transaction.type().name(),
                outgoing ? EntryDirection.DEBIT : EntryDirection.CREDIT,
                outgoing ? transaction.toAccountId() : transaction.fromAccountId(),
                MoneyFormatter.format(transaction.amount(), transaction.currency()),
                transaction.currency(),
                transaction.status().name(),
                transaction.createdAt());
    }
}
