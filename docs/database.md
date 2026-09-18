# Database

PostgreSQL 17. The schema is owned exclusively by Flyway migrations in
[`src/main/resources/db/migration`](../src/main/resources/db/migration).
Migrations merged to `main` are never edited.

## Entity relationships

```mermaid
erDiagram
    users ||--o{ accounts : "owns"
    users ||--o{ transactions : "initiates"
    accounts ||--o{ transactions : "from / to"
    accounts ||--o{ ledger_entries : "posted to"
    transactions ||--|{ ledger_entries : "balanced by"
    users ||--o{ audit_events : "acted"
    transactions ||--o{ audit_events : "audited by"

    users {
        bigint id PK
        varchar identity_issuer "OIDC iss"
        varchar identity_subject "OIDC sub"
        varchar role "CUSTOMER, TELLER, ADMIN"
        varchar status "ACTIVE, LOCKED, DISABLED"
    }
    accounts {
        bigint id PK
        bigint user_id FK "null for bank-internal accounts"
        char3 currency
        numeric balance "never negative"
    }
    transactions {
        bigint id PK
        varchar request_id UK "idempotency key"
        bigint initiated_by_user_id FK
        varchar transaction_type
        varchar status
        bigint from_account_id FK
        bigint to_account_id FK
        numeric amount "positive"
        char3 currency
    }
    audit_events {
        bigint id PK
        uuid event_id UK
        timestamptz occurred_at
        bigint actor_user_id FK "null if unauthenticated"
        varchar action
        varchar outcome "SUCCESS, REJECTED, FAILED"
        varchar reason_code "required unless SUCCESS"
        bigint transaction_id FK "null for rejected attempts"
        varchar correlation_id
        jsonb metadata
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
| An external identity maps to exactly one user | `uq_users_external_identity` |
| Roles and statuses come from a closed set | `ck_users_role`, `ck_users_status` |
| The ledger is append-only | trigger `trg_ledger_entries_append_only` rejects `UPDATE` and `DELETE` |
| The audit trail is append-only | trigger `trg_audit_events_append_only` rejects `UPDATE` and `DELETE` |
| Every non-successful audit event has a reason | `ck_audit_events_reason` |
| Audited users and transactions cannot be deleted | foreign keys without `ON DELETE` actions |

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
| V3 | Append-only business audit trail |
