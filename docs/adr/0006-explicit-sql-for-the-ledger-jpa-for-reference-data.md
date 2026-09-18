# ADR-0006: Use explicit SQL for the ledger and JPA only for reference data

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

The service has two very different kinds of persistence:

- **Money movement** (accounts, transactions, ledger entries, audit events):
  correctness depends on the exact statements and their order. Rows are locked
  with `SELECT ... FOR UPDATE` in a defined sequence, request ids are claimed
  with `INSERT ... ON CONFLICT DO NOTHING`, debits carry a guard predicate,
  history queries depend on a specific `UNION ALL` shape to use their indexes,
  and several statements rely on PostgreSQL features.
- **Reference data** such as a customer's saved payees: small aggregates with
  plain create, read, update and delete operations, where the main risks are
  lost updates and accidental cross-customer access.

An ORM hides the SQL it generates and decides when to flush. That is a
productivity gain for the second kind and a correctness risk for the first.

## Decision

- Accounts, transactions, the ledger and the audit trail are accessed through
  hand-written SQL with Spring's `JdbcClient`. Every statement is visible in
  code review.
- Payees are mapped with JPA (Hibernate) and Spring Data repositories, with:
  - `ddl-auto=validate`: Flyway owns the schema; Hibernate only verifies it;
  - `open-in-view=false`: no lazy loading outside a transaction;
  - `@Version` optimistic locking, with the version exposed to clients;
  - no entity associations to users, and identity-based equality;
  - owner-scoped finders as the only lookups used by services.
- A table is accessed through exactly one of the two mechanisms. JPA and
  `JdbcClient` never write to the same table.
- Both share one `DataSource` and one transaction manager, so a payee change
  and its audit event commit atomically.

## Consequences

- The money-moving code is explicit and reviewable, and it can use PostgreSQL
  features and locking precisely.
- The payee module is concise and gets optimistic locking and timestamps
  without hand-written SQL.
- Two persistence styles must be understood by maintainers. The boundary is
  simple to state: SQL for the ledger, JPA for reference data.
- Hibernate statistics are enabled in tests so that N+1 regressions in JPA
  queries are caught by assertions on the number of statements.
- With JPA present, Spring translates repository exceptions through the JPA
  dialect. Repositories therefore throw data access exceptions, never
  `IllegalStateException`, for unexpected update counts.

## Alternatives considered

- **JPA everywhere:** pessimistic locks, `ON CONFLICT` and the history query
  would need native queries or careful tuning of flush order, while hiding
  exactly the statements that most need review.
- **JDBC everywhere:** workable, but repetitive for simple CRUD and would need
  hand-written optimistic locking.
