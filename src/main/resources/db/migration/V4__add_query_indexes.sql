-- Purpose: indexes for the read paths.
--
-- Every index serves a named query; see docs/database.md. Primary keys and
-- unique constraints already provide indexes for id lookups, request_id and
-- (identity_issuer, identity_subject), so none are duplicated here. PostgreSQL
-- does not index foreign key columns automatically.
--
-- Plain CREATE INDEX is used because Flyway runs each migration in a
-- transaction, where CREATE INDEX CONCURRENTLY is not allowed. On a large
-- production table these would be created concurrently in a separate,
-- non-transactional migration.

-- "List my accounts": WHERE user_id = ? ORDER BY id
CREATE INDEX idx_accounts_user_id ON accounts (user_id);

-- Transaction history, newest first, keyset-paginated on (created_at, id).
-- One index per side because the history query is a UNION ALL of the
-- outgoing and the incoming branch; each branch reads its index in order and
-- stops after one page.
CREATE INDEX idx_transactions_from_created_id ON transactions (from_account_id, created_at DESC, id DESC);
CREATE INDEX idx_transactions_to_created_id ON transactions (to_account_id, created_at DESC, id DESC);

-- "All postings of an account", for statements and reconciliation.
CREATE INDEX idx_ledger_entries_account_transaction ON ledger_entries (account_id, transaction_id);
