# ADR-0011: Publish integration events through a transactional outbox, at least once

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

Other systems react to completed money movements: customer notifications,
fraud monitoring, statements. They must learn of every movement that
committed and of none that rolled back. The ledger is in PostgreSQL and the
events go to Kafka. No transaction spans both, so writing to the database and
then sending to the broker can fail between the two steps either way:

- **Commit, then send fails:** the money moved but nobody is told.
- **Send, then commit fails:** consumers act on a movement that never happened.

## Decision

- The service that completes a transaction appends a `TransactionCompleted`
  row to `outbox_events` in the same database transaction. The event exists
  if and only if the movement committed. An idempotent retry of a request
  creates no second event, because it posts nothing.
- A scheduled publisher claims due events in one statement
  (`FOR UPDATE SKIP LOCKED` plus a lease on `next_attempt_at`) and sends them
  to Kafka **after** that statement has committed. No row lock is held during
  network I/O, and concurrent publisher instances take disjoint events.
- An event is marked published only after the broker acknowledges it
  (`acks=all`, idempotent producer). A failed send is retried with back-off,
  and the error is kept on the row for operations.
- Delivery is therefore **at least once**. Every event carries a unique
  `eventId`; consumers record the ids they have processed in
  `processed_events`, in the same transaction as their own effects, and ignore
  duplicates.
- Messages are keyed by transaction id. Ordering across transactions is not
  guaranteed, because a retried event can overtake later ones; consumers must
  not depend on it.
- Payloads carry identifiers, amount and currency only: no names, account
  numbers, tokens or free text.
- Published events are deleted after a retention period, in bounded batches.

## Consequences

- No event is lost and no event describes a movement that rolled back.
- Consumers must be idempotent. The example consumer shows the pattern.
- Events arrive with a delay of up to the publisher interval, plus back-off
  while the broker is unavailable. The unpublished backlog and the age of the
  oldest unpublished event are the operational signals for a stuck publisher.
- `outbox_events` receives one insert per completed movement. The partial index
  on unpublished rows keeps claiming cheap as published rows accumulate until
  cleanup.

## Alternatives considered

- **Send to Kafka inside the database transaction:** the send cannot be rolled
  back, and a slow broker holds account locks.
- **Send after commit from the request thread:** an event is lost if the
  process stops between commit and send.
- **Distributed (XA) transactions:** Kafka does not take part in XA, and
  two-phase commit couples the availability of both systems.
- **Change data capture (for example Debezium) on the ledger tables:** a
  sound option at larger scale, but it publishes table rows instead of
  deliberate business events and needs extra infrastructure. The outbox
  table is compatible with a later move to CDC.
