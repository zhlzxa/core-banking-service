package io.github.zhlzxa.corebanking.ledger;

/**
 * Append-only access to the ledger. There are deliberately no update or delete operations: the
 * ledger is corrected with offsetting entries, and the database rejects mutation as well.
 */
public interface LedgerRepository {

    void append(LedgerEntry entry);
}
