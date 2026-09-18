#!/usr/bin/env bash
# Creates the key pair of the local demo identity provider: the demo scripts
# sign tokens with the private key and the service verifies them with the
# public key. The keys are generated on each machine and never committed;
# *.pem is ignored by git.
set -euo pipefail

KEYS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/keys"
mkdir -p "$KEYS"
if [[ -f "$KEYS/private.pem" ]]; then
    echo "Demo keys already exist in $KEYS"
    exit 0
fi
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEYS/private.pem" 2> /dev/null
openssl pkey -in "$KEYS/private.pem" -pubout -out "$KEYS/public.pem"
echo "Created demo key pair in $KEYS"
