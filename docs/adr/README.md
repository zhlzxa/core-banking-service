# Architecture Decision Records

This directory records significant design decisions: the context in which
they were made, the options considered and their consequences. Records are
numbered in the order they were written and are never rewritten; a decision
that changes is superseded by a new record.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-lock-accounts-in-ascending-id-order.md) | Lock accounts pessimistically in ascending id order | Accepted |
| [0002](0002-idempotency-through-a-unique-request-id.md) | Make money movements idempotent through a unique request id | Accepted |
| [0003](0003-delegate-authentication-to-an-oidc-provider.md) | Delegate authentication to an OIDC provider and authorise in three layers | Accepted |
| [0004](0004-audit-success-in-transaction-and-failure-independently.md) | Audit successes inside the business transaction and failures independently | Accepted |
| [0005](0005-keyset-pagination-for-transaction-history.md) | Paginate transaction history with a keyset cursor instead of OFFSET | Accepted |
| [0006](0006-explicit-sql-for-the-ledger-jpa-for-reference-data.md) | Use explicit SQL for the ledger and JPA only for reference data | Accepted |
| [0007](0007-enforce-cumulative-limits-with-an-atomic-conditional-upsert.md) | Enforce cumulative limits with an atomic conditional upsert | Accepted |
| [0008](0008-attribute-staff-and-machine-actions-to-the-customer.md) | Attribute staff and machine actions to the customer they serve | Accepted |
| [0009](0009-treat-payment-timeouts-as-unknown-and-reconcile.md) | Treat external payment timeouts as unknown and resolve them by reconciliation | Accepted |
| [0010](0010-correct-postings-with-reversal-transactions.md) | Correct postings with reversal transactions, never by editing the ledger | Accepted |

## Template

```markdown
# ADR-NNNN: <decision as a short imperative phrase>

- **Status:** Proposed | Accepted | Superseded by ADR-NNNN
- **Date:** YYYY-MM-DD

## Context
The forces at play: requirements, constraints, risks.

## Decision
What we do, stated so that it can be checked in code review.

## Consequences
Positive and negative outcomes, and the constraints this places on future work.

## Alternatives considered
Each option and why it was not chosen.
```
