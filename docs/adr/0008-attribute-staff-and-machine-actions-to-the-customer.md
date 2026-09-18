# ADR-0008: Attribute staff and machine actions to the customer they serve

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

Money no longer moves only when customers act for themselves. Tellers take in
and pay out cash at a branch, and ATMs dispense cash without any bank employee
involved. Several questions follow:

- Cash has to come from and go somewhere in a double-entry ledger.
- An investigator asking "what happened on this customer's account?" must find
  actions performed by tellers and machines as well as by the customer.
- Channel information (branch, ATM, online) drives risk rules and reporting, so
  it must be trustworthy.
- A machine is not a person. It must not be able to use endpoints designed for
  people, and people must not be able to impersonate a machine.
- Large cash payouts are a classic fraud and error risk for a single employee.

## Decision

- **Internal accounts.** Cash is booked against an internal account per currency
  (`CASH-HKD`, `CASH-USD`), identified by a stable code. Internal accounts have
  no owner and may go negative because they carry the bank's side of a posting.
  A CHECK constraint ties account type, owner and code together, and customer
  balances remain non-negative.
- **Actor and customer are recorded separately.** Every audit event records the
  actor (user or terminal, with branch) and, separately, the customer the action
  was performed for (`on_behalf_of_user_id`, indexed). For self-service both are
  the customer.
- **The channel is derived, never supplied.** `API` for customers, `BRANCH` for
  tellers, `ATM` for terminals, determined from how the caller authenticated.
  A teller's branch comes from a claim in their token. A teller without a branch
  cannot handle cash.
- **Machines have their own identity.** A terminal is registered with the OAuth2
  client identity it authenticates with and receives the role `ATM`, never a
  bank user role. `/atm/**` accepts only terminals, and every other endpoint
  refuses them, independently of token scopes.
- **Four eyes for large teller withdrawals.** At or above a configured threshold
  the teller (maker) only records a request; a different teller (checker)
  approves it, which pays out the cash, or rejects it. The rule is enforced by
  the service and by a CHECK constraint. A decision is made once, on a locked
  row. Unanswered requests expire, and expiry is persisted even though the
  approval call fails.

## Consequences

- One indexed query answers "everything that happened on behalf of this
  customer", whoever performed it.
- A compromised customer token cannot call terminal endpoints, and a stolen
  terminal credential cannot move money between customer accounts.
- All cash movements of a currency update the same internal account row. This
  serialises cash operations per currency; a production system would book
  against per-branch or per-terminal till accounts and settle them to the
  central cash account periodically.
- The service trusts the card network and the terminal for card holder
  verification. It enforces terminal identity, a per-withdrawal cap, idempotency
  and account rules, but not card or PIN checks.
- The approval threshold is a single figure for all currencies; per-currency
  thresholds are a straightforward extension.

## Alternatives considered

- **Crediting customer accounts without a counter-entry for cash:** breaks the
  double-entry invariant and makes the ledger impossible to reconcile.
- **Accepting the channel or terminal id from the request body:** trivially
  spoofable.
- **Giving ATMs a bank user account:** blurs the difference between people and
  machines and would let a terminal credential use people endpoints.
- **Four-eyes by convention only:** unverifiable; the database constraint makes
  the rule hold even for data written outside the service.
