# Architecture Decision Records

This directory records significant design decisions: the context in which
they were made, the options considered and their consequences. Records are
numbered in the order they were written and are never rewritten; a decision
that changes is superseded by a new record.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-lock-accounts-in-ascending-id-order.md) | Lock accounts pessimistically in ascending id order | Accepted |
| [0002](0002-idempotency-through-a-unique-request-id.md) | Make money movements idempotent through a unique request id | Accepted |

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
