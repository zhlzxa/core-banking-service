#!/usr/bin/env bash
# A large cash withdrawal needs a second teller: the requesting teller cannot
# approve it, a colleague can.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

AMY=$(token teller-amy "bank.cash" HK001)
BEN=$(token teller-ben "bank.cash" HK001)

step "Teller Amy pays out HKD 60,000.00 from Alice's account: above the threshold, so it waits for approval"
RESPONSE=$(call POST /teller/withdrawals "$AMY" \
    '{"requestId":"'"$(request_id)"'","accountId":1001,"amount":"60000.00","currency":"HKD"}')
echo "$RESPONSE"
APPROVAL=$(field approvalId "$RESPONSE")

step "Amy tries to approve her own request"
call POST "/teller/approvals/$APPROVAL/approve" "$AMY"

step "Teller Ben approves it and the cash is paid out"
call POST "/teller/approvals/$APPROVAL/approve" "$BEN"
