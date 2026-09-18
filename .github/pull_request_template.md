## Summary

<!-- What does this change do and why is it needed? -->

## Design notes

<!-- Invariants, transaction boundaries, locking, idempotency, security
     implications. Link the ADR if a decision was recorded. -->

## Testing

<!-- Which tests prove the behaviour, including failure paths? -->

## Checklist

- [ ] `./mvnw verify` passes locally
- [ ] Database changes are delivered as a new Flyway migration
- [ ] No secrets, tokens or real personal data in code, tests or logs
- [ ] Error responses expose stable codes only, never internals
- [ ] Documentation (README, ADR, API docs) updated where relevant
- [ ] CHANGELOG updated under `[Unreleased]`
