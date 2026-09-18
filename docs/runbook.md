# Operations runbook

How to tell whether the service is healthy, what each alert means and what
to do about it.

## Ports and dependencies

| Port | Serves | Exposure |
|---|---|---|
| 8080 (`SERVER_PORT`) | Customer, branch and terminal API | Through the API gateway |
| 8081 (`MANAGEMENT_PORT`) | Probes, info, Prometheus metrics | Cluster-internal only; never routed by the ingress |

| Dependency | If it is unavailable |
|---|---|
| PostgreSQL | No request can be served. Readiness goes down and traffic is withdrawn. |
| Kafka | Money movements continue. Events wait in the outbox and are published when the broker returns. |
| FPS | Payments to other banks stay `PROCESSING` and are resolved by reconciliation. Nothing is refunded automatically. |
| OIDC provider | Tokens are verified locally with the provider's signing keys, which are cached once discovered. Validation continues until the provider rotates its keys; the service can start while the provider is down. |

## Health probes

| Endpoint | Checks | Orchestrator action on failure |
|---|---|---|
| `/actuator/health/liveness` | The process only | Restart the instance |
| `/actuator/health/readiness` | Process accepting traffic and database reachable | Stop routing traffic; do not restart |

A database outage must not cause restarts: every instance would restart at
once and gain nothing. Kafka and FPS are deliberately not part of readiness,
for the reasons in the table above. Probe responses contain only the overall
status; component details are never exposed.

## Logs

Logs are JSON, one event per line, in the Logstash format. Every event written
while serving a request carries `correlationId`, the value of the
`X-Correlation-Id` response header. Clients receive the same id in every
error response, so a customer complaint can be traced from the id to the
log events and to the audit trail:

```sql
SELECT occurred_at, action, outcome, reason_code, transaction_id
FROM audit_events
WHERE correlation_id = :correlationId
ORDER BY occurred_at;
```

Logs never contain tokens, credentials or account numbers. Events identify
money movements by transaction id and FPS payments by end-to-end id.
`ObservabilityIT` verifies this for the main flows. Set `LOG_FORMAT=` (empty)
for plain-text logs on a developer machine; the `dev` profile does this.

## Metrics

Metrics are scraped from `/actuator/prometheus`. Every series carries the tag
`application="core-banking-service"`.

| Metric | Type | Meaning |
|---|---|---|
| `corebanking_business_events_total{action, outcome, reason}` | Counter | Business outcomes as recorded in the audit trail, counted after commit |
| `corebanking_fps_calls_seconds{operation, outcome}` | Timer | Calls to FPS; `outcome` is `accepted`, `rejected`, `no_response`, `error`, or the answer to a status query |
| `corebanking_fps_payments_processing` | Gauge | FPS payments awaiting an outcome |
| `corebanking_fps_payments_processing_oldest_age_seconds` | Gauge | Age of the oldest of them |
| `corebanking_fps_payments_needs_investigation` | Gauge | FPS payments escalated to operations |
| `corebanking_fps_payments_needs_investigation_oldest_age_seconds` | Gauge | Age of the oldest escalated payment |
| `corebanking_outbox_unpublished` | Gauge | Integration events not yet acknowledged by Kafka |
| `corebanking_outbox_oldest_unpublished_age_seconds` | Gauge | Age of the oldest of them |
| `http_server_requests_seconds{uri, method, status}` | Timer | API latency and status by endpoint template, with histogram buckets |
| `hikaricp_connections_*` | Gauges | Database connection pool usage |

Tags contain enum names, stable reason codes and endpoint templates only,
never account, customer or transaction identifiers, so the number of series
is bounded.

## Alerts

| Alert | Condition (PromQL) | Severity | First response |
|---|---|---|---|
| ServiceNotReady | `up == 0` or readiness failing for 2 m | Page | Check database connectivity and pool metrics |
| ApiServerErrors | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m])) > 0.01` | Page | Search logs at `ERROR` level; group by `correlationId` |
| TransferLatencyHigh | `histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/transfers"}[5m]))) > 1` for 10 m | Ticket | Check `hikaricp_connections_pending` and database lock waits |
| ConnectionPoolExhausted | `hikaricp_connections_pending > 0` for 5 m | Page | Look for long transactions in `pg_stat_activity` |
| FpsPaymentNeedsInvestigation | `corebanking_fps_payments_needs_investigation > 0` | Page (business hours) | [Investigate an escalated FPS payment](#investigate-an-escalated-fps-payment) |
| FpsOutcomesDelayed | `corebanking_fps_payments_processing_oldest_age_seconds > 900` | Ticket | Check FPS availability and reconciler logs |
| FpsNotResponding | `sum(rate(corebanking_fps_calls_seconds_count{outcome="no_response"}[5m])) / sum(rate(corebanking_fps_calls_seconds_count[5m])) > 0.05` | Page | Contact FPS operations; payments are safe and will reconcile |
| OutboxStalled | `corebanking_outbox_oldest_unpublished_age_seconds > 300` | Page | [Drain a stalled outbox](#drain-a-stalled-outbox) |
| AuthenticationFailureSpike | `sum(rate(corebanking_business_events_total{action="AUTHENTICATION_FAILED"}[5m])) > 1` | Security | Inform the security operations centre; review `audit_events` by source |
| AuditWriteFailed | Log event `Failed to record audit event` at `ERROR` | Page | Restore audit storage; the audit gap must be reported to compliance |

Thresholds are starting points and are tuned against observed traffic.

## Procedures

### Investigate an escalated FPS payment

A payment is escalated after reconciliation has failed to learn its outcome
the maximum number of times. The customer's account has been debited and the
money sits on the FPS clearing account. **Never refund without confirmation
from FPS**: if FPS did settle the payment, a refund pays the customer twice.

1. List escalated payments, oldest first:

   ```sql
   SELECT id, end_to_end_id, amount, currency, created_at, attempt_count, last_error_code
   FROM transactions
   WHERE status = 'NEEDS_INVESTIGATION'
   ORDER BY created_at;
   ```

2. Ask FPS operations for the status of each `end_to_end_id`.
3. Review the history of the payment in `audit_events` by `transaction_id`.
4. Record the outcome FPS confirms. The service does not yet provide a
   back-office operation for this step; until it does, the outcome is applied
   as a reviewed change under four-eyes control: a confirmed payment becomes
   `COMPLETED`, and a rejected payment is returned with a reversal
   transaction, as the service does for a rejection it receives itself.

### Drain a stalled outbox

1. Check `last_error` on the oldest unpublished events:

   ```sql
   SELECT event_id, event_type, occurred_at, attempt_count, next_attempt_at, last_error
   FROM outbox_events
   WHERE published_at IS NULL
   ORDER BY next_attempt_at
   LIMIT 20;
   ```

2. If the errors point to the broker, restore Kafka connectivity. Publication
   resumes on its own with back-off; no action is needed on the rows.
3. If there are no errors and no attempts, the publisher is not running:
   check `corebanking.outbox.publisher.enabled` and the instance logs.
4. Never delete or edit unpublished events. Consumers deduplicate by
   `event_id`, so events published late or twice are safe.

### Trace a customer complaint

1. Get the `X-Correlation-Id` from the client's error response, or find the
   request in `audit_events` by `request_id`.
2. Search the logs for that `correlationId`.
3. Use `transaction_id` from the audit trail to inspect the transaction and
   its ledger entries.
