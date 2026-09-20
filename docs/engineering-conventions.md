# Engineering conventions

**This is a single-author project and it does not take outside contributions.**
Pull requests will be closed without review, and issues are turned off. The
repository is published to be read, not to be worked on.

What follows is the set of rules I hold myself to while working on it. They are
written down for two reasons: the history of a financial system has to stay
reviewable and auditable, and a rule that exists only in my head is one I will
quietly drop on a busy day.

## Branching model

- `main` is always releasable and is only updated through reviewed
  merges.
- Work happens on short-lived branches named `<type>/<short-description>`,
  for example `feature/audit-log` or `fix/limit-upsert-first-insert`.
- Branches are merged with `--no-ff` so every delivered change set is
  visible as one merge commit.
- Releases follow [Semantic Versioning](https://semver.org/). A
  `chore(release): prepare X.Y.Z` commit sets the version and dates the
  [CHANGELOG.md](CHANGELOG.md) entry; the resulting merge on `main` is
  tagged `vX.Y.Z`.

## Commit messages

Commits follow [Conventional Commits 1.0](https://www.conventionalcommits.org/):

```
<type>(<scope>): <summary>

<body: what changed and, above all, why>

<footer: Refs: ADR-000N, BREAKING CHANGE: ...>
```

- **type**: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`,
  `ci`, `chore`.
- **scope** (optional): the business capability or technical area, for
  example `transfer`, `account`, `audit`, `security`, `payee`, `fps`,
  `outbox`, `db`, `api`, `obs`.
- **summary**: imperative mood, lower case, no trailing full stop, at
  most 72 characters.
- **body**: explains the reason for the change and any invariant it
  establishes or relies on. Reference the relevant ADR when a design
  decision is involved.

Each commit must compile and pass the full test suite. Do not mix
unrelated changes in one commit.

## Coding standards

- Formatting is enforced by Spotless (palantir-java-format). Run
  `./mvnw spotless:apply` before committing.
- Monetary amounts are always `BigDecimal`, created from strings and
  compared with `compareTo`. Never use `double` or `float` for money.
- Business exceptions extend `RuntimeException` so that they roll back
  the surrounding transaction.
- Javadoc on public types and methods describes responsibility,
  invariants, transactional behaviour and failure modes. Inline comments
  explain *why*, not *what*.
- Never log or return secrets, access tokens, full account numbers or
  exception internals.

## Database migrations

- Schema changes are made only through Flyway migrations in
  `src/main/resources/db/migration`.
- A migration that has been merged to `main` is immutable. Fix mistakes
  with a new migration.
- Each migration starts with a comment stating its purpose.

## Tests

| Kind | Naming | Runs with | Infrastructure |
|---|---|---|---|
| Unit | `*Test` | Surefire (`test`) | none |
| Integration | `*IT` | Failsafe (`verify`) | PostgreSQL via Testcontainers |

`./mvnw verify` must pass locally before a branch is merged. Line
coverage below 80% fails the build.
