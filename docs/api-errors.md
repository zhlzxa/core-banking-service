# API Error Contract

Every error response is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)
problem document with media type `application/problem+json`.

```json
{
  "type": "https://corebanking.example/problems/insufficient-balance",
  "title": "Insufficient balance",
  "status": 409,
  "detail": "The source account has insufficient balance",
  "instance": "/transfers",
  "code": "INSUFFICIENT_BALANCE",
  "correlationId": "3b0f6c7e-2d4a-4a91-9d3e-5f1b8c2a7e40"
}
```

| Member | Guarantee |
|---|---|
| `code` | Stable, machine-readable identifier. Clients branch on this value, never on `title` or `detail`. Codes are added but never renamed or reused. |
| `correlationId` | Identifies the request in logs and audit records. Quote it in support enquiries. Equal to the `X-Correlation-Id` request header when a well-formed one was supplied. |
| `detail` | Fixed, human-readable sentence. Never contains internal identifiers, SQL, stack traces or echoed request values. |
| `errors` | Present for `VALIDATION_FAILED` only: a list of `{field, reason}`. The rejected value itself is never echoed. |

## Catalogue

| HTTP | `code` | Meaning | Client action |
|---|---|---|---|
| 400 | `VALIDATION_FAILED` | One or more fields are missing or invalid | Correct the listed fields |
| 400 | `MALFORMED_REQUEST` | The body is not valid JSON or has the wrong structure | Fix the request encoding |
| 400 | `INVALID_TRANSFER` | The instruction is structurally invalid, for example source equals destination | Correct the instruction |
| 400 | `INVALID_AMOUNT_SCALE` | The amount has more decimal places than the currency allows (for example fractional JPY) | Round to the currency's minor unit |
| 400 | `CURRENCY_NOT_SUPPORTED` | The bank does not handle cash in this currency | Use a supported currency |
| 400 | `INVALID_CURSOR` | The pagination cursor is malformed or was altered | Restart from the first page |
| 401 | `UNAUTHENTICATED` | Token missing, invalid, expired, for another audience, or the user is unknown or inactive | Obtain a new token; do not retry blindly |
| 403 | `ACCESS_DENIED` | The token lacks the required scope or the user lacks the required role | Request the proper scope or role |
| 403 | `FOUR_EYES_REQUIRED` | The teller tried to decide their own withdrawal request | Ask another teller |
| 404 | `ACCOUNT_NOT_FOUND` | The account does not exist or is not visible to the caller | Check the account identifier |
| 404 | `TRANSFER_NOT_FOUND` | The transfer does not exist or does not involve the caller's accounts | Check the transfer identifier |
| 404 | `APPROVAL_NOT_FOUND` | The withdrawal approval does not exist | Check the approval identifier |
| 404 | `PAYMENT_NOT_FOUND` | The FPS payment does not exist or was not sent from the caller's accounts | Check the payment identifier |
| 404 | `PAYEE_NOT_FOUND` | The payee does not exist or belongs to another customer | Reload the payee list |
| 409 | `INSUFFICIENT_BALANCE` | The source balance does not cover the amount | Retry with the same `requestId` once funded |
| 409 | `SOURCE_ACCOUNT_NOT_ACTIVE` | The source account is frozen, dormant or closed | Contact the bank |
| 409 | `DESTINATION_ACCOUNT_CLOSED` | The destination account is closed | Use another destination |
| 409 | `TRANSFER_LIMIT_EXCEEDED` | The amount exceeds the per-transaction limit or the remaining daily limit | Transfer less, or wait for the next business day |
| 409 | `INVALID_ACCOUNT_STATE` | A back-office operation is not allowed in the account's current status | Check the account status |
| 409 | `CURRENCY_MISMATCH` | The instruction currency differs from an account currency | Correct the currency |
| 409 | `IDEMPOTENCY_KEY_REUSED` | The `requestId` was already used for a different instruction | Use a new `requestId` |
| 409 | `PAYEE_ALREADY_EXISTS` | The destination is already saved as a payee | Use the existing payee |
| 409 | `APPROVAL_NOT_PENDING` | The approval has already been decided | Reload the approval |
| 409 | `APPROVAL_EXPIRED` | Nobody decided the approval in time; it is now expired | Submit a new withdrawal request |
| 409 | `CONCURRENT_MODIFICATION` | The resource changed since the client read it | Reload, then reapply the change if still wanted |
| 500 | `INTERNAL_ERROR` | Unexpected failure; details are in the server log under the correlation id | Retry with the same `requestId`; contact support with the `correlationId` |

Protocol-level errors produced by the web framework (for example `404` for an
unknown path, `405` for an unsupported method, `415` for an unsupported
content type) use the HTTP status name as `code`, for example
`METHOD_NOT_ALLOWED`.

## Retrying

All money-moving endpoints are idempotent per `requestId`. After a timeout or
a `5xx`, retry with the **same** `requestId` and the same instruction: the
original result is returned if the first attempt succeeded, and nothing is
executed twice.

A request rejected with a `4xx` business error leaves no trace, so the same
`requestId` may be reused once the cause has been resolved.
