-- Purpose: outgoing payments through FPS (Hong Kong's Faster Payment System).
--
-- An FPS payment leaves the bank, so its outcome is decided outside this
-- database. The customer is debited immediately against an FPS clearing
-- account and the payment stays PROCESSING until FPS confirms or rejects it.
-- A timeout is not a failure: the outcome is unknown and is resolved by
-- reconciliation, never by an automatic refund.
--
--   PROCESSING           debited, outcome not yet known
--   COMPLETED            accepted by FPS
--   REVERSED             rejected by FPS; a REVERSAL transaction returned the money
--   NEEDS_INVESTIGATION  outcome still unknown after all retries; a person decides
--
-- external_status tracks the last known state at FPS:
--   NOT_SENT, SENT, ACCEPTED, REJECTED, UNKNOWN

ALTER TABLE transactions DROP CONSTRAINT ck_transactions_type;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_type
    CHECK (transaction_type IN ('TRANSFER', 'DEPOSIT', 'WITHDRAWAL', 'FPS_PAYMENT', 'REVERSAL'));

ALTER TABLE transactions DROP CONSTRAINT ck_transactions_status;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_status
    CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'PROCESSING', 'REVERSED', 'NEEDS_INVESTIGATION'));

ALTER TABLE transactions
    -- ISO 20022 end-to-end identification; at most 35 characters. Resends reuse it,
    -- so FPS can recognise a duplicate of a payment it has already processed.
    ADD COLUMN end_to_end_id           VARCHAR(35),
    ADD COLUMN external_status         VARCHAR(20),
    ADD COLUMN creditor_bank_code      VARCHAR(20),
    ADD COLUMN creditor_account        VARCHAR(34),
    ADD COLUMN attempt_count           INT            NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at         TIMESTAMPTZ,
    ADD COLUMN last_error_code         VARCHAR(50),
    -- A reversal points at the transaction it reverses.
    ADD COLUMN original_transaction_id BIGINT         REFERENCES transactions (id),
    ADD CONSTRAINT uq_transactions_end_to_end_id UNIQUE (end_to_end_id),
    ADD CONSTRAINT uq_transactions_reversal_once UNIQUE (original_transaction_id),
    ADD CONSTRAINT ck_transactions_external_status CHECK (
        external_status IS NULL
        OR external_status IN ('NOT_SENT', 'SENT', 'ACCEPTED', 'REJECTED', 'UNKNOWN')),
    -- An external payment always carries its end-to-end id and destination.
    ADD CONSTRAINT ck_transactions_fps_fields CHECK (
        transaction_type <> 'FPS_PAYMENT'
        OR (end_to_end_id IS NOT NULL AND external_status IS NOT NULL
            AND creditor_bank_code IS NOT NULL AND creditor_account IS NOT NULL));

-- The reconciler's work queue: unresolved payments ordered by when they are due.
CREATE INDEX idx_transactions_reconciliation ON transactions (next_attempt_at)
    WHERE status = 'PROCESSING';

-- Automated jobs such as reconciliation act through the SYSTEM channel.
ALTER TABLE audit_events DROP CONSTRAINT ck_audit_events_channel;
ALTER TABLE audit_events ADD CONSTRAINT ck_audit_events_channel
    CHECK (channel IN ('API', 'BRANCH', 'ATM', 'SYSTEM'));

INSERT INTO accounts (account_type, code, currency, balance)
VALUES ('INTERNAL', 'FPS-CLEARING-HKD', 'HKD', 0),
       ('INTERNAL', 'FPS-CLEARING-CNY', 'CNY', 0);
