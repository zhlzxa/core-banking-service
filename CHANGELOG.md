# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.0] - 2026-09-18

### Added

- OAuth2 resource server authentication with RS256 JWTs from an external
  OpenID Connect provider, validating signature, issuer, audience and expiry.
- Bank users identified by external (issuer, subject) with bank-owned roles
  and statuses; unknown, locked and disabled users are rejected.
- Three-layer authorization for transfers: OAuth scope `bank.transfer`, role
  `CUSTOMER` and ownership of the source account.
- The initiating user is recorded on every transaction and included in the
  idempotency comparison.
- ADR-0003 describing the authentication and authorization model.

### Changed

- `POST /transfers` requires a bearer token. Accounts of other customers and
  bank-internal destination accounts are reported as `ACCOUNT_NOT_FOUND`.

## [0.2.0] - 2026-09-18

### Added

- Core double-entry ledger schema (accounts, transactions, ledger entries)
  with database-enforced invariants and an append-only ledger.
- Atomic internal transfers with pessimistic locking in ascending account id
  order and idempotency through a unique client request id.
- `POST /transfers` endpoint with Bean Validation and RFC 9457 problem
  responses carrying stable error codes.
- Integration tests against PostgreSQL for rollback, concurrent transfers,
  overdraft races and concurrent duplicate requests.
- ADR-0001 (lock ordering), ADR-0002 (idempotency) and schema documentation.

### Fixed

- Secret scanning in CI failed on the repository's root commit.

## [0.1.0] - 2026-09-18

### Added

- Spring Boot 4 project skeleton on Java 21 with the Maven wrapper.
- Build quality gates: Spotless formatting, JaCoCo coverage threshold and
  separate unit and integration test phases.
- GitHub Actions pipeline with test reports, gitleaks secret scanning and
  Dependabot updates.
- Docker Compose definition for a local PostgreSQL 17 instance.
- Contribution guide, security policy and pull request template.

[Unreleased]: https://github.com/zhlzxa/core-banking-service/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/zhlzxa/core-banking-service/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/zhlzxa/core-banking-service/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/zhlzxa/core-banking-service/releases/tag/v0.1.0
