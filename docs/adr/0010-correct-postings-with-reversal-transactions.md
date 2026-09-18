# ADR-0010: Correct postings with reversal transactions, never by editing the ledger

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

Money sometimes has to go back after a posting has committed: FPS rejects a
payment that was already debited, or an operation is found to be wrong. The
ledger is the bank's accounting record. Auditors and reconciliation processes
must be able to see every movement that ever happened, including those that
were later undone.

## Decision

- Ledger entries are append-only, enforced by a database trigger and by the
  repository having no update or delete operation.
- An undo is a new transaction of type `REVERSAL` that posts the same amount in
  the opposite direction and references the original through
  `original_transaction_id`.
- The reversal's request id is derived from the original transaction id, and a
  unique constraint on `original_transaction_id` makes a second reversal of the
  same transaction impossible.
- The original transaction's status records the outcome (`REVERSED`); its ledger
  entries are not touched.

## Consequences

- The full history remains visible: the original debit and credit, and the
  offsetting reversal, each with its own timestamp and audit trail.
- Balances are always the sum of the entries; there is no second place where
  corrections live.
- A reversed payment shows twice in the account history, once each way, which
  matches how banks present returned payments.

## Alternatives considered

- **Updating or deleting the original entries:** destroys the audit trail and
  breaks every report already produced from them.
- **A "cancelled" flag on entries:** balances would have to be computed with
  filters, and the correction would not be a balanced posting in its own right.
