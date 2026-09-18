# ADR-0003: Delegate authentication to an OIDC provider and authorise in three layers

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

Banks run a central identity platform that handles credentials, multi-factor
authentication, device binding and session policy. A core banking service
should not store passwords or issue its own tokens: doing so would duplicate
that platform and widen the attack surface.

Knowing *who* the caller is does not answer *what* they may do. A token proves
the identity and what the client application was allowed to request (its
scopes). The bank's role model and account ownership live in the bank's own
records.

## Decision

- The service is an **OAuth2 resource server**. It accepts RS256-signed JWT
  access tokens from one configured issuer and rejects any token whose issuer,
  audience or validity period does not match. The same validator chain is
  applied whether keys are discovered from the issuer or read from a
  configured public key.
- A token is mapped to a bank user through the pair **(issuer, subject)**,
  which is stable for a person. Usernames and e-mail addresses are never used
  as identifiers. Unknown identities and locked or disabled users receive 401.
- Authorization is enforced in three independent layers:
  1. **Scope** (from the token): the client application is allowed to perform
     the kind of operation, for example `bank.transfer`.
  2. **Role** (from the bank's database): the person has a role permitted to
     perform it, for example `CUSTOMER`.
  3. **Resource ownership** (from the bank's database): the person owns the
     account being acted on.
- Scope and role checks are declared with `@PreAuthorize` on service methods.
  Ownership is checked on the locked account rows inside the transaction.
- An account that exists but is not owned by the caller is reported as
  `404 ACCOUNT_NOT_FOUND`, indistinguishable from a missing account.

## Consequences

- No credentials are stored by the service; compromise of this service does
  not expose passwords.
- A new endpoint cannot bypass scope and role checks, because they sit on the
  service method rather than on the URL.
- Every authenticated request performs one indexed lookup of the user. This is
  acceptable; a short-lived cache can be added if it ever shows up in latency
  measurements.
- Local development and tests need tokens. Tests mint tokens with key pairs
  generated at runtime; no private key is ever stored in the repository.

## Alternatives considered

- **Username/password with sessions:** duplicates the identity platform and
  requires CSRF protection and credential storage.
- **Roles taken from token claims:** the bank would lose control over
  authorization decisions to the identity provider's configuration.
- **Returning 403 for other customers' accounts:** confirms that the account
  exists, which leaks information useful for enumeration.
