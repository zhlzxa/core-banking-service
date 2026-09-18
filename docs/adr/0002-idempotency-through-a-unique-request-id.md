# ADR-0002: Make money movements idempotent through a unique request id

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

Clients call the transfer API over unreliable networks. After a timeout the
client cannot tell whether the transfer was executed, and the only safe action
is to retry. Without protection a retry can move the money twice.

The guarantee must hold for concurrent duplicates too: a mobile app and its
retry may reach two application instances at the same moment.

## Decision

- Every money movement carries a client-generated `requestId` (for example a
  UUID), which the client reuses for every retry of the same instruction.
- `transactions.request_id` has a `UNIQUE` constraint. The service claims the
  key with `INSERT ... ON CONFLICT (request_id) DO NOTHING RETURNING id`
  inside the transfer transaction.
- If the key is already taken, the stored transaction is compared with the new
  instruction (accounts, amount compared numerically, currency):
  - same instruction: the original transaction is returned with the original
    status code, and no money moves;
  - different instruction: the request is rejected with
    `409 IDEMPOTENCY_KEY_REUSED`.
- A request rejected by a business rule rolls back completely, including the
  key claim, so the client may retry with the same key once the cause is fixed.

## Consequences

- Duplicate execution is prevented by the database, which also covers
  concurrent duplicates across application instances: the second insert waits
  on the unique index until the first transaction commits or rolls back.
- `ON CONFLICT` is used instead of catching a duplicate-key exception, because
  PostgreSQL aborts the whole transaction after a failed statement.
- The response of a retry is rebuilt from the stored transaction rather than
  replayed byte for byte. This is sufficient because the transaction row holds
  everything the response contains.
- Keys are unique across all clients. Keys must therefore be random (UUIDs);
  predictable keys would let clients collide with each other.

## Alternatives considered

- **Idempotency key in a separate table with a stored response:** more general
  (it works for any endpoint) but adds a second write per request and
  duplicates data already in `transactions`.
- **Deduplication in an application cache:** does not survive restarts and is
  not atomic with the money movement.
- **Relying on clients not to retry:** not acceptable for payments.
