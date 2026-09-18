# Observability standard

What we can see in production, the rules that keep it that way, and what is
missing. The tool choice is [ADR-0009](../architecture/adr/0009-observability-stack.md);
running the local stack is [ops/README.md](../../ops/README.md).

## What we have

| Signal | Where | State |
|--------|-------|-------|
| Logs | JSON to stdout, collected by Promtail into Loki | Implemented |
| Metrics | Micrometer, scraped by Prometheus from `/api/actuator/prometheus` | Implemented |
| Health | `/api/actuator/health`, `/health/liveness`, `/health/readiness` | Implemented |
| Traces | Nothing. One trace id per request in the logs, no spans, no propagation | Not implemented |

There is one service, so a trace id is enough to follow a request today. That
stops being true the moment a second service exists.

## Logging

Format is decided by the profile: a human-readable pattern locally, one JSON
object per line (Elastic Common Schema) in `prod` and `staging` through
`logging.structured.format.console: ecs`. Nothing writes log files; the container
writes to stdout and the platform collects it.

Every line carries the trace id from MDC key `traceId`. In the ECS output that
becomes a top-level field also named `traceId` — not `trace.id`, which is what
an ECS reader might expect. `log.level` and `message` are real ECS names. The
[promtail config](../../ops/promtail/promtail-config.yml) documents the same
mapping.

Levels:

| Level | Use for |
|-------|---------|
| `ERROR` | We are broken and someone has to look: an unexpected exception, a 5xx, a failed outbox delivery. |
| `WARN` | Something a caller did wrong, or a degraded state we recovered from: a 4xx, a failed login, a retry that succeeded. |
| `INFO` | One line per completed request, lifecycle events, security-relevant events. Not a running commentary on a method. |
| `DEBUG` | Detail for a local investigation. Off in every shared environment. Never assume it is on. |

Rules:

- A 4xx is the caller's mistake, so it is a `WARN`, not an `ERROR`. Paging on a
  client sending bad JSON wastes the on-call.
- Log an exception with the throwable, not `e.getMessage()`, or the stack trace
  is gone.
- Never log inside a loop over rows. One line per request is the budget.
- Never build log strings by concatenation; use the placeholder form so a
  disabled level costs nothing.

### Never log

User passwords or hashes, tokens (access, refresh or reset), the JWT secret,
full card numbers, CVV, internal database ids of users, or a whole request body
on a successful request. `RequestLoggingFilter` masks `password`, `oldPassword`,
`newPassword`, `confirmPassword`, `token`, `accessToken`, `refreshToken`,
`secret`, `cardNumber` and `cvv` to `"***"`. **Extend that list in the same
commit that adds a new sensitive field**, because the filter only masks names it
knows.

### The request log

`platform/web/RequestLoggingFilter` logs exactly one line per HTTP request:
method, path, status, duration in ms, the caller's public UUID (or `anonymous`)
and the client IP. `INFO` for 2xx and 3xx, `WARN` for 4xx, `ERROR` for 5xx.
`/api/actuator/**` is not logged, so the 15-second scrape does not drown
everything else.

For a failed request (status >= 400) it also logs the JSON request body,
truncated to 2 KB, with the sensitive values masked. That is the only case where
a body is logged.

### The audit log

`platform/audit/AuditLog` writes security-relevant events to a dedicated logger
named `audit`, so a platform can route it to its own file or index:
`REGISTERED`, `LOGIN_SUCCEEDED`, `LOGIN_FAILED`, `TOKEN_REFRESHED`,
`PASSWORD_CHANGED`. Each event records the actor (public UUID or `anonymous`),
the outcome and the trace id.

It is log-based. There is no database-backed audit trail and no retention
guarantee beyond whatever the log store keeps, which locally is 7 days. A
queryable audit table is a separate decision, not a config change.

## Metrics

`micrometer-registry-prometheus` is on the classpath and the actuator exposes
`health`, `info`, `metrics` and `prometheus` under `/api/actuator/` on port 8080.

Access rules: `health` and `info` are public. `GET /api/actuator/prometheus` and
`/api/actuator/metrics` are permitted **only** from private and loopback ranges
(10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.1, ::1). Any other source
address gets 401. Prometheus therefore has to run inside the private network, or
reach the app through something that does.

Every series carries the common tag `application=groceryecom`, and percentile
histograms are enabled for `http.server.requests`, which is what makes
server-side `histogram_quantile` work.

The ones that matter:

| Metric | Reads as |
|--------|----------|
| `http_server_requests_seconds_count` | Request rate, by `uri`, `method`, `status`, `outcome` |
| `http_server_requests_seconds_bucket` | Latency histogram; p95 and p99 per `uri` |
| `jvm_memory_used_bytes{area="heap"}` / `jvm_memory_max_bytes{area="heap"}` | Heap headroom |
| `jvm_gc_pause_seconds_*` | GC pauses |
| `hikaricp_connections_active` / `_idle` / `_pending` | Database pool pressure |
| `process_cpu_usage`, `system_cpu_usage` | CPU |

New custom metrics go through Micrometer's `MeterRegistry`. Tag values must be
bounded: `uri` templates and status codes are fine, user ids, order ids and trace
ids are not. One series per user is how a metrics backend falls over.

## Alerting

There is no Alertmanager and no alert rule yet — `rule_files` in the Prometheus
config is empty. These are the thresholds to start with when it gets wired up.
They are starting points to tune against real traffic, not sacred numbers.

| Alert | Condition | Why |
|-------|-----------|-----|
| High error rate | 5xx share > 1% for 5 minutes | Above background noise, below "wait for a customer to call" |
| Slow requests | p95 > 500 ms for 10 minutes | The `http.server.requests` SLO buckets stop at 500 ms for a reason |
| Pool exhaustion | `hikaricp_connections_pending` > 0 sustained for 5 minutes | Requests are queueing for a connection; next stop is timeouts |
| Readiness failing | `/health/readiness` failing on an instance for 2 minutes | Readiness is database-only, so this means the database |
| Heap pressure | Heap used / max > 90% for 10 minutes | The container exits on OOM, so catch it before the restart |
| No metrics | Scrape target down for 5 minutes | An instance we cannot see is an instance we cannot judge |

## Debugging a customer report

1. Get the `traceId` from the customer. Every error response carries it in the
   `ApiResponse` envelope, so it is on the screen or in their screenshot.
2. In Grafana, Explore, datasource Loki:
   `{container="grocery_ecom_api"} | json | traceId = "<the id>"`.
   That returns every line of that request across instances: the request log
   line, any audit event, and the stack trace if it failed.
3. The request log line gives status, duration, the caller's public UUID and the
   client IP. For a 4xx or 5xx the masked request body is on that line too.
4. Widen from there: same `uri` on the latency and error panels of the
   [API dashboard](../../ops/grafana/provisioning/dashboards/json/groceryecom-api.json)
   around that timestamp answers "only them" versus "everyone".
5. If the trace id returns nothing, the request never reached the app. Check the
   load balancer and whether the instance was ready at the time.

Never ask a customer for their password or token to reproduce a problem. The
public UUID is enough to find their account.

## Gaps

- **No distributed tracing.** No OpenTelemetry, no spans, no context
  propagation. The trace id is generated per request by `RequestIdFilter` and
  only lives in logs. It does not cross a queue or an outbound HTTP call.
- **No Alertmanager.** Nothing pages anyone. Somebody has to be looking at
  Grafana, which means nobody is.
- **Log retention is 7 days locally** and undefined elsewhere, because there is
  nowhere else yet ([staging](../development/staging.md) does not exist).
- **No audit table.** Security events are logs; they can be lost with the log
  store.
- **No SLOs.** There are SLO buckets on the histogram but no agreed target, so
  "too slow" is currently an opinion.
- **No frontend or synthetic monitoring.** We see requests that arrive, not users
  who gave up.

## See also

- [ops/README.md](../../ops/README.md) — running the stack, adding a panel
- [ADR-0009](../architecture/adr/0009-observability-stack.md) — why these tools
- [Coding standard](coding-standard.md) — logging rules in code review
- [Security standard](security-standard.md) — what must never be exposed
- [Deployment](../development/deployment.md) — health endpoints, env vars
