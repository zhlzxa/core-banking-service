#!/usr/bin/env bash
# A transfer the balance cannot cover is rejected with a stable error code and
# leaves no trace in the ledger.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

BOB=$(token bob "bank.transfer bank.accounts.read")

step "Bob's balance"
call GET /accounts/2001 "$BOB"

step "Bob tries to transfer HKD 5,000.00 to Alice"
call POST /transfers "$BOB" \
    '{"requestId":"'"$(request_id)"'","fromAccountId":2001,"toAccountId":1001,"amount":"5000.00","currency":"HKD"}'

step "Bob tries to transfer from Alice's account: it does not exist as far as Bob is concerned"
call POST /transfers "$BOB" \
    '{"requestId":"'"$(request_id)"'","fromAccountId":1001,"toAccountId":2001,"amount":"1.00","currency":"HKD"}'

step "Bob's balance is unchanged"
call GET /accounts/2001 "$BOB"
