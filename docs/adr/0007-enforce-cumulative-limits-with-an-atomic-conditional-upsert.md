# ADR-0007: Enforce cumulative limits with an atomic conditional upsert

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

Customer accounts have a daily limit on outgoing transfers. A single-transfer
limit is a simple comparison, but a cumulative limit depends on every other
transfer from the same account on the same day, including ones running
concurrently on other application instances.

The obvious implementation, summing today's transfers and then inserting the
new one, is a check-then-act race: two transfers of 30,000 against a 50,000
limit can both read a total of 0 and both succeed.

Two further details matter:

- "Today" must be the bank's business day. For a Hong Kong bank, a transfer at
  01:00 local time belongs to the new day although it is still the previous day
  in UTC.
- A transfer that fails after consuming part of the limit, for example because
  a later step throws, must give that share back.

## Decision

- One row per account and business day in `daily_transfer_usage` holds the
  amount consumed so far.
- The limit is consumed with a single statement that both checks and
  increments:

  ```sql
  INSERT INTO daily_transfer_usage (account_id, usage_date, used_amount)
  SELECT :accountId, :businessDay, :amount
  WHERE :amount <= :limit
  ON CONFLICT (account_id, usage_date) DO UPDATE
      SET used_amount = daily_transfer_usage.used_amount + EXCLUDED.used_amount
      WHERE daily_transfer_usage.used_amount + EXCLUDED.used_amount <= :limit
  RETURNING used_amount
  ```

  No returned row means the limit would be exceeded. Both the insert branch and
  the update branch are guarded, so even the first transfer of the day cannot
  exceed the limit on its own.
- The statement runs inside the transfer transaction, so a rollback also rolls
  back the increment.
- The business day comes from `BusinessCalendar`, which applies the configured
  zone (`Asia/Hong_Kong`) to the injectable application clock.

## Consequences

- The limit holds under concurrency independently of other locks. In the
  transfer flow the source account row is already locked, so this is defence
  in depth there, but the repository is safe to use from any other flow.
- The check costs one indexed statement instead of an aggregate over the day's
  transactions, and the cost does not grow with the number of transfers.
- The usage table is derived data. It can be rebuilt from the transactions table
  if ever needed; it is not an accounting record.
- Business days are calendar days in one zone. Holiday calendars and cut-off
  times are not modelled.

## Alternatives considered

- **`SELECT sum(amount)` then insert:** races under concurrency unless every
  caller also locks the account, which couples correctness to a convention.
- **A running total column on `accounts`:** needs a separate reset job at
  midnight and conflates limit tracking with the balance row, the most
  contended row in the system.
- **`SERIALIZABLE` isolation:** correct, but turns contention into retries.
