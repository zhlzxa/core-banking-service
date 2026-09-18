# ADR-0005: Paginate transaction history with a keyset cursor instead of OFFSET

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

Customers scroll through their transaction history in a mobile app. Active
accounts accumulate tens of thousands of transactions, and new transactions
can arrive while a customer is paging.

`LIMIT n OFFSET m` has two problems here:

- **Cost grows with depth.** The database must produce and discard `m` rows
  before returning the page, so deep pages get progressively slower.
- **Pages shift.** A transaction that arrives between two requests pushes
  every row down by one, so the next page repeats a row the customer has
  already seen. A deletion would skip one instead.

A cursor on the timestamp alone is not sufficient either: several
transactions can share a `created_at` value, and rows at a page boundary with
the same timestamp would be skipped or duplicated.

## Decision

- History is ordered by `(created_at DESC, id DESC)`, which is a total order.
- The client receives an opaque `nextCursor` encoding the `(created_at, id)`
  of the last row returned, and passes it back to get the next page. The
  next page is selected with the row-value comparison
  `(created_at, id) < (:createdAt, :id)`.
- The history query is a `UNION ALL` of the outgoing branch
  (`from_account_id = ?`) and the incoming branch (`to_account_id = ?`), each
  ordered and limited separately, then merged. Each branch is served by its
  own composite index `(account_side, created_at DESC, id DESC)`.
- One row more than the page size is fetched to determine `hasMore`; there is
  no total count.
- The cursor is Base64 for transport convenience only. It is decoded as
  untrusted input and rejected with `400 INVALID_CURSOR` if malformed.

## Consequences

- Page cost is independent of depth. Measured with 200,000 transactions over
  200 accounts, the plan is a Merge Append of two index-only scans with no
  sort, executing in about 0.1 ms.
- Pages are stable under concurrent inserts: a new transaction never causes a
  repeat or a gap in the pages that follow.
- Clients cannot jump to an arbitrary page number or show "page 3 of 40".
  This is acceptable for an infinite-scroll history; statements for a period
  are a separate use case.
- The response carries no total count, which would require counting the whole
  history on every request.

## Alternatives considered

- **OFFSET pagination:** simple, but slow for deep pages and unstable under
  concurrent inserts.
- **A single query with `from_account_id = ? OR to_account_id = ?`:** cannot
  use either index for ordering, so every page would sort the account's full
  history.
- **Timestamp-only cursor:** loses or duplicates rows that share a timestamp.
