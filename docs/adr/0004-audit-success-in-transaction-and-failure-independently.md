# ADR-0004: Audit successes inside the business transaction and failures independently

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

Regulators and internal investigators expect the bank to answer, for any
account and point in time, who did what and whether it worked. Two failure
modes would make the audit trail untrustworthy:

- **Money moves without an audit record.** If the audit event of a transfer is
  written in a separate transaction and that write fails after the transfer
  commits, the trail silently misses a completed movement.
- **Rejected attempts vanish.** A rejected transfer rolls back its database
  transaction. An audit event written inside that transaction is rolled back
  too, so the attempt, which is often the most interesting event for fraud
  analysis, leaves no trace.

The two outcomes therefore need opposite transaction strategies.

## Decision

- **Success:** the `TRANSFER_COMPLETED` event is appended inside the transfer
  transaction. It commits or rolls back together with the balances, ledger
  entries and transaction status. If it cannot be written, the transfer fails.
- **Rejection and failure:** the `TRANSFER_REJECTED` or `TRANSFER_FAILED` event
  is written by `IndependentAuditRecorder` in a `REQUIRES_NEW` transaction, so it
  survives the rollback of the business transaction. The recorder is a separate
  Spring bean because a `REQUIRES_NEW` method invoked on `this` would bypass the
  transactional proxy and silently join the outer transaction.
- **Security rejections** (401 and 403) are recorded in the same independent way
  from the security layer.
- Writing a rejection event is **best effort**: a failure is logged at `ERROR`
  and the client still receives the original business response.
- The actor always comes from the authenticated principal. Reason codes are the
  stable API error codes, never exception messages. Access tokens, credentials
  and similar secrets are never recorded.
- The table is append-only, enforced by a trigger as well as by the repository
  interface.

## Consequences

- Every completed transfer has exactly one success event; an idempotent retry
  returns before any audit write.
- A rejected request temporarily uses a second database connection for the
  independent transaction while the first is still open. The connection pool
  must allow at least two connections per concurrently audited request.
- If the audit store is unavailable, transfers stop completing (they fail
  closed), while rejections continue to be answered (they fail open). Failing
  open for rejections is a deliberate choice to keep the API responsive; it is
  monitored through the `ERROR` log and should be alerted on.
- Auditing security rejections can be used to fill the table with anonymous
  401 events. Rate limiting at the edge is expected to bound this.

## Alternatives considered

- **Audit everything in one independent transaction after the business
  transaction:** a crash between the two commits loses the success event.
- **Audit everything inside the business transaction:** loses every rejected
  attempt.
- **Asynchronous audit through a message broker:** introduces the dual-write
  problem between database and broker; the transactional outbox (introduced
  later for integration events) would be required to make it reliable.
