# ADR-0012: Scale out with stateless instances on one primary database; shard by account later

- **Status:** Accepted
- **Date:** 2026-09-19

## Context

The service must handle growth in customers and transaction volume without
giving up the guarantees it is built on: atomic postings, a balanced ledger,
idempotent requests and serialised updates per account. These guarantees
currently rely on a single PostgreSQL database: one transaction covers both
legs of a transfer, row locks serialise movements on an account, and unique
constraints decide idempotency.

A retail bank's core ledger typically sees a few hundred to a few thousand
postings per second at peak. A single well-provisioned PostgreSQL primary
sustains that when transactions are short and indexed, which the design
ensures: no network I/O inside transactions, locks held for milliseconds,
keyset pagination, partial indexes for work queues.

## Decision

- **Application tier:** scale horizontally. Instances hold no state; all
  state, including background-job coordination, is in PostgreSQL. Background
  jobs use database leases (`FOR UPDATE SKIP LOCKED`), so they scale with the
  instances and need no leader election.
- **Database tier:** one primary for all writes and for reads that must be
  current (balances before a movement). Read replicas may serve transaction
  history and reporting, which tolerate a small replication lag. The
  connection pool per instance is bounded so that the total stays within
  what the primary can serve, or a pooler such as PgBouncer in transaction
  mode is placed in front of it.
- **Growth of history tables:** `transactions`, `ledger_entries`,
  `audit_events` and `outbox_events` can be range-partitioned by time when
  they grow large; the keyset indexes remain valid per partition.
- **When one primary is no longer enough:** shard by account. Each shard owns
  a set of accounts together with their ledger entries, so a movement between
  two accounts of the same shard stays a single local transaction. A movement
  across shards becomes a saga: debit the source into an internal in-transit
  account on its shard, credit the destination from the in-transit account on
  the other shard, each step idempotent by request id and recovered by
  reconciliation. This is the same pattern the service already uses for FPS,
  which is effectively a transfer to another shard that belongs to someone
  else.

## Consequences

- The current code needs no change to run many instances.
- Sharding would change transfers between shards from atomic to eventually
  consistent. Customers would see a pending state for them, as they already
  do for FPS payments. This cost is deferred until volume requires it.
- The ascending-id lock order stays valid within a shard.

## Alternatives considered

- **Sharding now:** adds sagas, routing and cross-shard reporting before any
  measured need, and weakens atomicity for every cross-shard transfer.
- **A distributed SQL database** (for example CockroachDB or YugabyteDB):
  keeps multi-row transactions across nodes, but with higher write latency
  from consensus, more contention on hot accounts, and less operational
  familiarity than PostgreSQL in most banks.
- **Event sourcing with balances as projections:** a strong audit model, but
  enforcing "never overdraw" requires serialising per account anyway, and the
  append-only ledger with reversal postings already provides a complete
  history.
