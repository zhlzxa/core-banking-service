#!/usr/bin/env bash
# FPS accepts a payment but the answer is lost. The customer is debited once,
# the payment stays PROCESSING, and reconciliation later learns from FPS that
# it completed. Nothing is refunded on the way.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

ALICE=$(token alice "bank.transfer bank.accounts.read")

step "Alice pays HKD 250.00 to an account at another bank; the FPS response times out"
RESPONSE=$(call POST /fps/payments "$ALICE" \
    '{"requestId":"'"$(request_id)"'","fromAccountId":1001,"creditorBankCode":"004","creditorAccount":"TIMEOUT-12345678","amount":"250.00","currency":"HKD"}')
echo "$RESPONSE"
PAYMENT=$(field transactionId "$RESPONSE")

step "Waiting for reconciliation to ask FPS what happened to payment $PAYMENT"
for _ in $(seq 1 20); do
    STATE=$(call GET "/fps/payments/$PAYMENT" "$ALICE")
    if ! grep -q '"status":"PROCESSING"' <<< "$STATE"; then
        break
    fi
    printf '.'
    sleep 3
done
echo
echo "$STATE"

step "FPS call outcomes and unresolved payments, as monitoring sees them"
curl -sS "${MANAGEMENT:-http://localhost:8081}/actuator/prometheus" \
    | grep -E '^corebanking_fps_(calls_seconds_count|payments_processing){' || true
