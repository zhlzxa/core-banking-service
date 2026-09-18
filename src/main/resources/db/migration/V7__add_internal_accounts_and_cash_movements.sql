-- Purpose: bank-internal accounts, cash deposits and withdrawals, and
-- attribution of actions performed on behalf of a customer.
--
-- Every movement is a balanced posting between two accounts. Cash that a
-- teller receives or an ATM dispenses is booked against an internal cash
-- account per currency, identified by a stable code rather than by a
-- hard-coded id.

ALTER TABLE accounts
    ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    ADD COLUMN code         VARCHAR(50);

-- Accounts created before this migration without an owner were internal.
UPDATE accounts SET account_type = 'INTERNAL', code = 'LEGACY-' || id WHERE user_id IS NULL;

ALTER TABLE accounts
    ADD CONSTRAINT ck_accounts_type CHECK (account_type IN ('CUSTOMER', 'INTERNAL')),
    ADD CONSTRAINT uq_accounts_code UNIQUE (code),
    -- A customer account has an owner; an internal account has a code instead.
    ADD CONSTRAINT ck_accounts_owner_matches_type CHECK (
        (account_type = 'CUSTOMER' AND user_id IS NOT NULL AND code IS NULL)
        OR (account_type = 'INTERNAL' AND user_id IS NULL AND code IS NOT NULL));

-- Internal accounts carry the bank's side of each posting, so their balance can
-- legitimately be negative (a cash account that has paid out more than it took
-- in). Customer balances still can never be negative.
ALTER TABLE accounts DROP CONSTRAINT ck_accounts_balance_non_negative;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_balance_non_negative
    CHECK (account_type = 'INTERNAL' OR balance >= 0);

INSERT INTO accounts (account_type, code, currency, balance)
VALUES ('INTERNAL', 'CASH-HKD', 'HKD', 0),
       ('INTERNAL', 'CASH-USD', 'USD', 0);

ALTER TABLE transactions DROP CONSTRAINT ck_transactions_type;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_type
    CHECK (transaction_type IN ('TRANSFER', 'DEPOSIT', 'WITHDRAWAL'));

-- Who acted and for whom. For a customer acting for themselves both are the
-- same user; for a teller or an ATM, the actor is staff or a machine and the
-- customer is recorded here, so "everything that happened on my account" is
-- one indexed query.
ALTER TABLE audit_events
    ADD COLUMN on_behalf_of_user_id BIGINT      REFERENCES users (id),
    ADD COLUMN actor_terminal_id    VARCHAR(50),
    ADD COLUMN actor_branch_code    VARCHAR(20),
    ADD CONSTRAINT ck_audit_events_channel CHECK (channel IN ('API', 'BRANCH', 'ATM'));

CREATE INDEX idx_audit_events_on_behalf_time ON audit_events (on_behalf_of_user_id, occurred_at DESC);
