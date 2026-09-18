-- Purpose: sample data for the demo profile only. Never loaded in production.
--
-- A repeatable migration runs after every versioned migration, so new schema
-- versions can be added without renumbering this file. The seed runs only on
-- an empty database and is otherwise a no-op.
--
-- Opening balances are funded with balanced cash deposits, exactly as the
-- service would post them, so every balance equals the sum of its ledger
-- entries from the start.

DO $$
DECLARE
    issuer CONSTANT VARCHAR := 'https://demo-idp.corebanking.local';
    cash_hkd BIGINT;
    cash_usd BIGINT;
    funding RECORD;
    tx BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE identity_issuer = issuer) THEN
        RETURN;
    END IF;

    INSERT INTO users (identity_issuer, identity_subject, display_name, role, status) VALUES
        (issuer, 'alice',     'Alice Chan',      'CUSTOMER', 'ACTIVE'),
        (issuer, 'bob',       'Bob Wong',        'CUSTOMER', 'ACTIVE'),
        (issuer, 'teller-amy', 'Amy Lee (teller)', 'TELLER',  'ACTIVE'),
        (issuer, 'teller-ben', 'Ben Ho (teller)',  'TELLER',  'ACTIVE'),
        (issuer, 'ops-admin', 'Operations admin', 'ADMIN',    'ACTIVE');

    INSERT INTO accounts (id, user_id, account_type, currency, balance, daily_transfer_limit)
    SELECT v.id, u.id, 'CUSTOMER', v.currency, 0, v.daily_limit
    FROM (VALUES
            (1001, 'alice', 'HKD', 100000.00),
            (1002, 'alice', 'USD', NULL),
            (2001, 'bob',   'HKD', NULL)) AS v (id, subject, currency, daily_limit)
    JOIN users u ON u.identity_issuer = issuer AND u.identity_subject = v.subject;

    INSERT INTO terminals (id, identity_issuer, identity_subject, terminal_type, branch_code, status)
    VALUES ('ATM-HK-0001', issuer, 'atm-hk-0001', 'ATM', 'HK001', 'ACTIVE');

    SELECT id INTO STRICT cash_hkd FROM accounts WHERE code = 'CASH-HKD';
    SELECT id INTO STRICT cash_usd FROM accounts WHERE code = 'CASH-USD';

    FOR funding IN
        SELECT * FROM (VALUES
            (1001, 'HKD', 200000.00),
            (1002, 'USD', 2000.00),
            (2001, 'HKD', 1000.00)) AS f (account_id, currency, amount)
    LOOP
        INSERT INTO transactions
            (request_id, transaction_type, status, from_account_id, to_account_id, amount, currency)
        VALUES
            ('demo-opening-' || funding.account_id, 'DEPOSIT', 'COMPLETED',
             CASE funding.currency WHEN 'HKD' THEN cash_hkd ELSE cash_usd END,
             funding.account_id, funding.amount, funding.currency)
        RETURNING id INTO tx;

        INSERT INTO ledger_entries (transaction_id, account_id, direction, amount, currency) VALUES
            (tx, CASE funding.currency WHEN 'HKD' THEN cash_hkd ELSE cash_usd END,
             'DEBIT', funding.amount, funding.currency),
            (tx, funding.account_id, 'CREDIT', funding.amount, funding.currency);

        UPDATE accounts SET balance = balance - funding.amount
        WHERE id = CASE funding.currency WHEN 'HKD' THEN cash_hkd ELSE cash_usd END;
        UPDATE accounts SET balance = balance + funding.amount WHERE id = funding.account_id;
    END LOOP;
END $$;
