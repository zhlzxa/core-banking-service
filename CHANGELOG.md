# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0] - 2026-09-18

### Added

- Append-only `audit_events` trail with database-enforced immutability and
  mandatory reason codes for non-successful outcomes.
- Transfers audited along the transaction boundary: `TRANSFER_COMPLETED`
  atomically with the money movement, `TRANSFER_REJECTED` and
  `TRANSFER_FAILED` in an independent transaction that survives rollback.
- Authentication failures and access denials audited with actor, endpoint and
  correlation id.
- ADR-0004 describing the audit transaction strategy.

### Changed

- A transfer fails if its success audit event cannot be written.
- Method-level authorization denials are handled by the security layer, like
  URL-level denials.

## [0.4.0] - 2026-09-18

### Added

- Correlation id for every request: accepted from a well-formed
  `X-Correlation-Id` header or generated, echoed in the response, included in
  every log line and every error body.
- Error code catalogue and retry guidance in `docs/api-errors.md`.

### Changed

- 401 and 403 responses raised by the security layer are RFC 9457 problem
  documents with `code` `UNAUTHENTICATED` or `ACCESS_DENIED`, like all other
  errors.
- Framework errors such as unknown paths or unsupported methods carry a
  `code` and correlation id.
- JSON numbers are deserialised into exact decimals.

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

[Unreleased]: https://github.com/zhlzxa/core-banking-service/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/zhlzxa/core-banking-service/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/zhlzxa/core-banking-service/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/zhlzxa/core-banking-service/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/zhlzxa/core-banking-service/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/zhlzxa/core-banking-service/releases/tag/v0.1.0
