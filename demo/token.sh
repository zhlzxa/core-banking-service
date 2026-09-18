#!/usr/bin/env bash
# Prints a demo access token, for example to paste into Swagger UI.
#
#   demo/token.sh alice "bank.transfer bank.accounts.read bank.payees.read bank.payees.write"
#   demo/token.sh teller-amy "bank.cash" HK001
#   demo/token.sh ops-admin "bank.accounts.admin"
#   demo/token.sh atm-hk-0001 "bank.atm.withdraw"
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

if [[ $# -lt 2 ]]; then
    echo "Usage: $0 <subject> \"<scopes>\" [branch code]" >&2
    exit 1
fi
token "$@"
echo
