# Security Policy

## Reporting a vulnerability

Please do not open a public issue for security problems. Report them
privately through
[GitHub Security Advisories](https://github.com/zhlzxa/core-banking-service/security/advisories/new).
You should receive an acknowledgement within five working days.

## Handling of secrets

- No credential, private key or access token is ever committed. Local
  values live in a git-ignored `.env` file; deployed environments inject
  them through environment variables.
- Any key material shipped for the local demo is clearly labelled as
  local-only and must never be trusted by a real environment.
- Every push is scanned with gitleaks in CI. A secret that reaches the
  history is treated as compromised and rotated, regardless of whether
  the commit is later rewritten.

## Data protection

The repository contains synthetic test data only. No real customer data,
account numbers or personal information may be added.
