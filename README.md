# Core Banking Service

[![CI](https://github.com/zhlzxa/core-banking-service/actions/workflows/ci.yml/badge.svg)](https://github.com/zhlzxa/core-banking-service/actions/workflows/ci.yml)

A core banking backend focused on the parts of a banking system that are
genuinely hard to get right: atomic money movement on a double-entry
ledger, idempotent payment APIs, concurrency control and an auditable
record of every business action.

**Stack:** Java 21 · Spring Boot 4 · PostgreSQL 17 · Flyway · Testcontainers · Docker

> **Status:** under active development. See [CHANGELOG.md](CHANGELOG.md)
> for what has been delivered so far.

## Quick start

Prerequisites: JDK 21 and Docker.

```bash
# Build and run all unit and integration tests
./mvnw verify

# Start a local PostgreSQL instance
docker compose up -d postgres
```

## Project documentation

| Document | Purpose |
|---|---|
| [CONTRIBUTING.md](CONTRIBUTING.md) | Branching, commit convention, coding standards |
| [SECURITY.md](SECURITY.md) | How to report a vulnerability, secret-handling rules |
| [CHANGELOG.md](CHANGELOG.md) | Release notes |

## License

[MIT](LICENSE)
