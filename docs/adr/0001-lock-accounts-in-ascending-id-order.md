# ADR-0001: Lock accounts pessimistically in ascending id order

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

A transfer reads and updates two account rows. Concurrent transfers touching
the same account must not both see the same starting balance, otherwise an
account can be overdrawn or an update lost.

Two transfers in opposite directions between the same pair of accounts
(A→B and B→A) that each lock their own source account first form a lock
cycle. PostgreSQL detects the deadlock and aborts one of them, which surfaces
as a sporadic failure under load.

Balances on hot accounts (for example an internal settlement account) are
contended, so an approach that retries on conflict would waste work exactly
when the system is busiest.

## Decision

- Both accounts are read with `SELECT ... FOR UPDATE` inside the transfer's
  database transaction, before any business rule is evaluated.
- Locks are always acquired in **ascending account id order**, independent of
  the transfer direction.
- Business checks (balance, currency and later status and limits) run on the
  locked rows, so they cannot be invalidated before commit.
- The debit statement additionally carries `AND balance >= :amount`, and the
  table has a `CHECK (balance >= 0)` constraint, as defence in depth.

## Consequences

- Transfers that share an account serialise on its row lock; transfers on
  disjoint accounts run fully in parallel.
- Deadlocks between transfers cannot occur, because every transaction
  acquires locks in the same global order.
- Throughput on a single very hot account is bounded by the duration of the
  transfer transaction. Transactions must therefore stay short and never
  perform remote calls while holding these locks.
- Any future operation that locks more than one account must follow the same
  ordering rule.

## Alternatives considered

- **Optimistic locking (version column and retry):** simple for
  low-contention data, but retries grow with contention and every caller has
  to handle retry exhaustion. Rejected for balances.
- **`SERIALIZABLE` isolation:** correct, but pushes serialisation failures and
  retries onto every transaction and makes behaviour under load harder to
  predict.
- **A single atomic `UPDATE ... WHERE balance >= :amount` without an explicit
  lock:** prevents overdrafts on its own, but other rules (currency, account
  status, limits) would still be evaluated on unlocked data.
