-- Purpose: account lifecycle status and transfer limits.
--
-- Status governs which movements an account may take part in:
--   ACTIVE   may send and receive
--   FROZEN   may receive but not send (for example under a court order)
--   DORMANT  may receive but not send until reactivated
--   CLOSED   terminal; takes part in no movement
--
-- Limits apply to customer transfers out of the account. NULL means the
-- account has no limit of that kind, which is the case for bank-internal
-- accounts; customer accounts are given limits when they are opened.

ALTER TABLE accounts
    ADD COLUMN status                 VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN status_reason          VARCHAR(30),
    ADD COLUMN per_transaction_limit  NUMERIC(19, 4),
    ADD COLUMN daily_transfer_limit   NUMERIC(19, 4),
    ADD CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'DORMANT', 'CLOSED')),
    ADD CONSTRAINT ck_accounts_status_reason CHECK (
        status_reason IS NULL
        OR status_reason IN ('COURT_ORDER', 'FRAUD_SUSPECTED', 'CUSTOMER_REQUEST', 'KYC_EXPIRED')),
    -- A frozen account must always say why.
    ADD CONSTRAINT ck_accounts_frozen_has_reason CHECK (status <> 'FROZEN' OR status_reason IS NOT NULL),
    ADD CONSTRAINT ck_accounts_limits_positive CHECK (
        (per_transaction_limit IS NULL OR per_transaction_limit > 0)
        AND (daily_transfer_limit IS NULL OR daily_transfer_limit > 0));

-- Amount already transferred out of an account per business day. One row per
-- account and day is updated atomically, so concurrent transfers cannot
-- together exceed the daily limit.
CREATE TABLE daily_transfer_usage (
    account_id   BIGINT         NOT NULL REFERENCES accounts (id),
    usage_date   DATE           NOT NULL,
    used_amount  NUMERIC(19, 4) NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, usage_date),
    CONSTRAINT ck_daily_transfer_usage_positive CHECK (used_amount > 0)
);
