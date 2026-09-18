# ADR-0009: Treat external payment timeouts as unknown and resolve them by reconciliation

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

An FPS payment leaves the bank: FPS decides whether it succeeds. The bank's
request to FPS can time out, and a timeout says nothing about the outcome:

- the request may never have reached FPS;
- FPS may have rejected it;
- FPS may have accepted it and credited the payee, with only the response lost.

Refunding the customer on a timeout would, in the third case, pay the money
twice: once to the payee and once back to the customer. Keeping a database
transaction open while waiting for FPS would hold row locks and a pooled
connection for as long as FPS takes, so an FPS slowdown would block unrelated
customers and exhaust the connection pool.

## Decision

- **Three phases, two local transactions.** (1) Validate, lock, debit the
  customer against the FPS clearing account, record the payment as
  `PROCESSING` with a new ISO 20022 end-to-end id; commit. (2) Send to FPS with
  no transaction open; the dispatcher refuses to run inside one. (3) Record the
  answer in a new transaction on the locked payment row.
- **Only an explicit rejection returns money.** Accepted: `COMPLETED`.
  Rejected: a reversal transaction returns the money and the daily limit is
  released (`REVERSED`). No answer: the payment stays `PROCESSING` with
  external status `UNKNOWN`, and the API answers `202 Accepted`.
- **Reconciliation resolves the unknown.** A scheduled job claims due payments
  with one `UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED) RETURNING`
  statement that also sets a lease. It queries FPS by end-to-end id and
  confirms, returns, reschedules with back-off, or, if FPS never received the
  payment, sends it again with the same end-to-end id.
- **Give up safely.** After the maximum number of attempts the payment becomes
  `NEEDS_INVESTIGATION`, audited and logged at `ERROR` for alerting. People
  resolve it; the system never guesses.
- **Every outcome is idempotent.** Outcomes are applied on a locked row and only
  while the payment is `PROCESSING`, so the synchronous answer and a later
  reconciliation cannot both take effect.
- **Crash safety.** A newly posted payment is already due for reconciliation
  after a grace period, so a process that stops between phases 1 and 2 leaves
  nothing stranded.

## Consequences

- A customer can never be paid out twice by a timeout.
- While a payment is `PROCESSING`, the money has left the customer's balance.
  This is the conservative choice; showing it as "pending" is a presentation
  concern for the channels.
- Concurrent reconciler instances work on disjoint payments without holding
  locks across network calls. If an instance dies, its lease expires and the
  payments become due again.
- FPS must honour the end-to-end id for deduplication; the simulated network
  used for local runs and tests does.
- `NEEDS_INVESTIGATION` requires an operational procedure; see the runbook.

## Alternatives considered

- **Refund on timeout:** can pay twice.
- **Call FPS inside the posting transaction:** holds locks and connections for
  an unbounded time and still does not solve the unknown outcome.
- **Reconcile with `SELECT ... FOR UPDATE` held during the FPS call:** keeps
  locks across network calls, the same problem in a different place.
- **Two-phase commit with FPS:** not offered by payment networks.
