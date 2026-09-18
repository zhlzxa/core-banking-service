# Database

PostgreSQL 17. The schema is owned exclusively by Flyway migrations in
[`src/main/resources/db/migration`](../src/main/resources/db/migration).
Migrations merged to `main` are never edited.

## Entity relationships

```mermaid
erDiagram
    accounts ||--o{ transactions : "from / to"
    accounts ||--o{ ledger_entries : "posted to"
    transactions ||--|{ ledger_entries : "balanced by"

    accounts {
        bigint id PK
        char3 currency
        numeric balance "never negative"
    }
    transactions {
        bigint id PK
        varchar request_id UK "idempotency key"
        varchar transaction_type
        varchar status
        bigint from_account_id FK
        bigint to_account_id FK
        numeric amount "positive"
        char3 currency
    }
    ledger_entries {
        bigint id PK
        bigint transaction_id FK
        bigint account_id FK
        varchar direction "DEBIT or CREDIT"
        numeric amount "positive"
        char3 currency
    }
```

## Invariants enforced by the database

| Invariant | Mechanism |
|---|---|
| A balance is never negative | `ck_accounts_balance_non_negative` |
| A request id is executed at most once | `uq_transactions_request_id` |
| Money cannot move from an account to itself | `ck_transactions_distinct_accounts` |
| Amounts are positive | `ck_transactions_amount_positive`, `ck_ledger_entries_amount_positive` |
| A ledger leg cannot be posted twice | `uq_ledger_entries_leg` |
| The ledger is append-only | trigger `trg_ledger_entries_append_only` rejects `UPDATE` and `DELETE` |

Every completed transaction has ledger legs whose debits equal its credits.
This is guaranteed by the service writing both legs in the same database
transaction, and it is asserted by the integration tests.

## Monetary values

Amounts are `NUMERIC(19, 4)`. The API validates the per-currency number of
decimal places; the extra scale leaves room for currencies with three minor
units.

## Migrations

| Version | Purpose |
|---|---|
| V1 | Core ledger: accounts, transactions, ledger entries |
| V2 | Users identified by external (issuer, subject); account ownership; transaction initiator |
