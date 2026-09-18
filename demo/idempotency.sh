#!/usr/bin/env bash
# A retried transfer is executed once; a reused key with a different
# instruction is rejected.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

ALICE=$(token alice "bank.transfer bank.accounts.read")
RID=$(request_id)
BODY='{"requestId":"'"$RID"'","fromAccountId":1001,"toAccountId":2001,"amount":"100.00","currency":"HKD"}'

step "Alice transfers HKD 100.00 to Bob (requestId $RID)"
call POST /transfers "$ALICE" "$BODY"

step "The client timed out and retries with the same requestId: the original result, no second debit"
call POST /transfers "$ALICE" "$BODY"

step "Reusing the requestId for a different amount is rejected"
call POST /transfers "$ALICE" "${BODY/100.00/999.00}"

step "Alice's account was debited once"
call GET /accounts/1001 "$ALICE"
