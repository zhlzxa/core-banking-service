#!/usr/bin/env bash
# Shared helpers for the demo scripts. For the local demo environment only:
# tokens are signed with the local demo key, which no real environment trusts.
set -euo pipefail

DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API="${API:-http://localhost:8080}"
ISSUER="https://demo-idp.corebanking.local"
AUDIENCE="core-banking-api"
PRIVATE_KEY="$DEMO_DIR/keys/private.pem"

b64url() {
    openssl base64 -A | tr '+/' '-_' | tr -d '='
}

# Prints a 15-minute access token: token <subject> "<scopes>" [branch code]
token() {
    local subject=$1 scopes=$2 branch=${3:-}
    if [[ ! -f "$PRIVATE_KEY" ]]; then
        echo "Missing $PRIVATE_KEY: run demo/generate-keys.sh first" >&2
        exit 1
    fi
    local now extra="" header payload unsigned signature
    now=$(date +%s)
    if [[ -n "$branch" ]]; then
        extra=",\"branch_code\":\"$branch\""
    fi
    header='{"alg":"RS256","typ":"JWT"}'
    payload=$(printf '{"iss":"%s","sub":"%s","aud":"%s","iat":%d,"exp":%d,"scope":"%s"%s}' \
        "$ISSUER" "$subject" "$AUDIENCE" "$now" "$((now + 900))" "$scopes" "$extra")
    unsigned="$(printf '%s' "$header" | b64url).$(printf '%s' "$payload" | b64url)"
    signature=$(printf '%s' "$unsigned" | openssl dgst -sha256 -sign "$PRIVATE_KEY" -binary | b64url)
    printf '%s.%s' "$unsigned" "$signature"
}

# A fresh idempotency key for each run of a script.
request_id() {
    printf 'demo-%s' "$(openssl rand -hex 6)"
}

# Calls the API and prints the body followed by the status: call <method> <path> <token> [json]
call() {
    local method=$1 path=$2 bearer=$3 body=${4:-}
    local args=(-sS -X "$method" -H "Authorization: Bearer $bearer" -w '\n<- HTTP %{http_code}\n')
    if [[ -n "$body" ]]; then
        args+=(-H 'Content-Type: application/json' -d "$body")
    fi
    curl "${args[@]}" "$API$path"
}

# Extracts a top-level numeric field from a JSON response: field <name> <json>
field() {
    sed -n "s/.*\"$1\":\([0-9]*\).*/\1/p" <<< "$2" | head -n 1
}

step() {
    printf '\n== %s\n' "$*"
}
